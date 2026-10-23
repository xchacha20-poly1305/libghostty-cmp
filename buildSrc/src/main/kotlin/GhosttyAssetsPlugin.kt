import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RelativePath
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import javax.inject.Inject

abstract class GhosttyThemesTask @Inject constructor(
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
    private val archiveOperations: ArchiveOperations,
) : GhosttyOutputTask() {
    @get:Input
    abstract val commit: Property<String>

    @get:Internal
    abstract val sourceDir: DirectoryProperty

    @TaskAction
    fun run() {
        val zonLines = File(sourceDir.get().asFile, "build.zig.zon").readLines()
        val anchor = zonLines.indexOfFirst { it.contains("iterm2_themes") }
        if (anchor < 0) throw GradleException("iterm2_themes not found in build.zig.zon")
        val entry = zonLines.subList(anchor, minOf(anchor + 4, zonLines.size))
        val url = zonString(entry, ".url")
        val hash = zonString(entry, ".hash")

        // Gradle downloads and unpacks the archive itself: zig fetch without
        // --save caches the package with an extra directory level
        // (https://codeberg.org/ziglang/zig/issues/37053), so zig only checks
        // the hash, against a private cache it cannot break.
        val archive = File(temporaryDir, "themes.tar.gz")
        URI(url).toURL().openStream().use { input ->
            archive.outputStream().use { input.copyTo(it) }
        }
        val zigCache = File(temporaryDir, "zig-cache")
        val fetchOutput = ByteArrayOutputStream()
        try {
            execOperations.exec {
                workingDir = temporaryDir
                commandLine("zig", "fetch", archive.absolutePath)
                environment("ZIG_GLOBAL_CACHE_DIR", zigCache.absolutePath)
                standardOutput = fetchOutput
            }
        } finally {
            fileSystemOperations.delete { delete(zigCache) }
        }
        val actualHash = fetchOutput.toString().trim()
        if (actualHash != hash) {
            throw GradleException("$url has hash $actualHash, build.zig.zon expects $hash")
        }

        val themes = outputDir.get().asFile
        fileSystemOperations.delete { delete(themes) }
        themes.mkdirs()
        extract(archive, themes)
        writeIndex(themes)
    }

    /** The string value of `key` (such as `.url`) among the lines of a ZON dependency entry. */
    private fun zonString(entry: List<String>, key: String): String {
        val line = entry.firstOrNull { it.trim().startsWith("$key ") }
            ?: throw GradleException("$key not found for iterm2_themes in build.zig.zon")
        return line.substringAfter('"').substringBefore('"')
    }

    private fun writeIndex(themes: File) {
        val themeFiles = themes.listFiles { file -> file.isFile }?.sortedBy { it.name } ?: emptyList()
        // AGP asset merging strips dotfiles from the AAR and again from every
        // consumer's merged assets.
        File(themes, "index").writeText(
            themeFiles.joinToString("") { file ->
                val background = file.useLines { lines ->
                    lines.firstNotNullOfOrNull { line ->
                        val separator = line.indexOf('=')
                        if (separator > 0 && line.substring(0, separator).trim() == "background") {
                            line.substring(separator + 1).trim()
                        } else {
                            null
                        }
                    }
                }.orEmpty()
                "${file.name}\t$background\n"
            }
        )
    }

    private fun extract(tarball: File, into: File) {
        fileSystemOperations.copy {
            from(archiveOperations.tarTree(tarball))
            into(into)
            eachFile {
                relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
            }
            includeEmptyDirs = false
        }
    }
}

abstract class GhosttyTerminfoTask @Inject constructor(
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) : GhosttyOutputTask() {
    @get:Input
    abstract val commit: Property<String>

    @get:Internal
    abstract val sourceDir: DirectoryProperty

    @TaskAction
    fun run() {
        val generator = File(temporaryDir, "generate-terminfo.zig")
        generator.writeText(
            """
            const std = @import("std");
            const terminfo = @import("ghostty_terminfo");

            pub fn main(init: std.process.Init) !void {
                var buffer: [4096]u8 = undefined;
                var stdout = std.Io.File.stdout().writerStreaming(init.io, &buffer);
                try terminfo.ghostty.encode(&stdout.interface);
                try stdout.end();
            }
            """.trimIndent()
        )
        val sourceFile = File(temporaryDir, "ghostty.terminfo")
        sourceFile.outputStream().use { output ->
            execOperations.exec {
                workingDir = temporaryDir
                commandLine(
                    "zig", "run",
                    "--dep", "ghostty_terminfo",
                    "-Mroot=${generator.absolutePath}",
                    "-Mghostty_terminfo=${File(sourceDir.get().asFile, "src/terminfo/main.zig").absolutePath}",
                )
                standardOutput = output
            }
        }
        val database = File(temporaryDir, "database")
        fileSystemOperations.delete { delete(database) }
        execOperations.exec {
            commandLine("tic", "-x", "-o", database.absolutePath, sourceFile.absolutePath)
        }
        // tic names the first-letter subdirectory "x" on Linux and the hex
        // form "78" on case-insensitive filesystems.
        val compiled = database.walkTopDown().firstOrNull { it.isFile && it.name == "xterm-ghostty" }
            ?: throw GradleException("xterm-ghostty not found under $database")
        val terminfo = outputDir.get().asFile
        fileSystemOperations.delete { delete(terminfo) }
        terminfo.mkdirs()
        compiled.copyTo(File(terminfo, "xterm-ghostty"))
    }
}

/**
 * Registers `ghosttyThemes` and `ghosttyTerminfo`, which write the theme catalog and the compiled terminfo entry
 * as resources under `build/ghostty-resources`; [GhosttyAssets.resourceDir] is that directory, built by both tasks.
 */
class GhosttyAssetsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val env = GhosttyEnv(project)
        val root = project.layout.buildDirectory.dir("ghostty-resources")
        val packageDir = root.map { it.dir(GhosttyAssets.PACKAGE_PATH) }

        val ghosttyThemes = env.register(
            "ghosttyThemes",
            GhosttyThemesTask::class.java,
            packageDir.map { it.dir("themes") },
        ) {
            commit.set(env.commit)
            sourceDir.set(env.sourceDir)
        }

        val ghosttyTerminfo = env.register(
            "ghosttyTerminfo",
            GhosttyTerminfoTask::class.java,
            packageDir.map { it.dir("terminfo") },
        ) {
            commit.set(env.commit)
            sourceDir.set(env.sourceDir)
        }

        project.extensions.add(
            "ghosttyAssets",
            GhosttyAssets(project.files(root).builtBy(ghosttyThemes, ghosttyTerminfo)),
        )
    }
}

class GhosttyAssets(val resourceDir: FileCollection) {
    companion object {
        const val PACKAGE_PATH = "io/github/xchacha20_poly1305/libghostty/extras"
    }
}
