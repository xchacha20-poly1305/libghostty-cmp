import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

abstract class GhosttySourceTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Internal
    abstract val repository: Property<String>

    @get:Input
    abstract val commit: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val patches: ConfigurableFileCollection

    @get:Internal
    abstract val sourceDir: DirectoryProperty

    init {
        outputs.upToDateWhen { (it as GhosttySourceTask).isCurrent() }
    }

    private fun isCurrent(): Boolean {
        val source = sourceDir.get().asFile
        if (!File(source, "build.zig").exists()) return false
        val head = ByteArrayOutputStream()
        val headResult = execOperations.exec {
            workingDir = source
            commandLine("git", "rev-parse", "HEAD")
            standardOutput = head
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        if (headResult.exitValue != 0 || head.toString().trim() != commit.get()) return false
        val patchFiles = patches.files.sortedBy { it.name }
        if (patchFiles.isEmpty()) return true
        val reverseCheck = execOperations.exec {
            workingDir = source
            commandLine(
                listOf("git", "apply", "--reverse", "--check") + patchFiles.map { it.absolutePath }
            )
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        return reverseCheck.exitValue == 0
    }

    @TaskAction
    fun run() {
        val source = sourceDir.get().asFile
        if (!File(source, "build.zig").exists()) {
            source.deleteRecursively()
            source.mkdirs()
            git(source, "init", "-q")
        }
        fetch(source)
        git(source, "checkout", "-qf", commit.get())
        git(source, "clean", "-qfd")
        for (patch in patches.files.sortedBy { it.name }) {
            git(source, "apply", patch.absolutePath)
        }
    }

    private fun fetch(source: File) {
        git(source, "fetch", "--depth", "1", repository.get(), commit.get())
    }

    private fun git(dir: File, vararg args: String) {
        execOperations.exec {
            workingDir = dir
            commandLine("git", *args)
        }
    }
}

fun ghosttyCommit(project: Project): String = project.providers.gradleProperty("GHOSTTY_COMMIT").get().also {
    if (!it.matches(Regex("[0-9a-f]{40}"))) {
        throw GradleException("GHOSTTY_COMMIT must be a full 40-character Git commit hash")
    }
}

internal class GhosttyEnv(private val project: Project) {
    val commit: String = ghosttyCommit(project)
    val patches = project.rootProject.layout.projectDirectory.dir("patches/ghostty")
        .asFileTree.matching {
            include("*.patch")
        }
    val buildDir = project.rootProject.layout.buildDirectory.dir("ghostty")
    val sourceDir = buildDir.map { it.dir("source") }
    val zigVersion: Provider<String> = project.providers.exec {
        commandLine("zig", "version")
    }.standardOutput.asText.map { it.trim() }

    /** A `ghosttyPrebuilt` export (`GHOSTTY_PREBUILT`) to copy outputs from instead of building them. */
    private val prebuiltDir: Directory? = project.providers.gradleProperty("GHOSTTY_PREBUILT").orNull
        ?.let { project.rootProject.layout.projectDirectory.dir(it) }

    val source = rootTask<GhosttySourceTask>("ghosttySource") {
        repository.set("https://github.com/ghostty-org/ghostty.git")
        commit.set(this@GhosttyEnv.commit)
        patches.from(this@GhosttyEnv.patches)
        sourceDir.set(this@GhosttyEnv.sourceDir)
    }

    /**
     * Registers [name] as a [type] task building into [outputDir] from the ghostty source and adds its output to
     * the `ghosttyPrebuilt` export, under [name]. With `GHOSTTY_PREBUILT`, registers [name] as a
     * [GhosttyPrebuiltTask] copying that export's output instead, so the build needs neither zig nor the source.
     */
    fun <T : GhosttyOutputTask> register(
        name: String,
        type: Class<T>,
        outputDir: Provider<Directory>,
        configure: T.() -> Unit,
    ): TaskProvider<GhosttyOutputTask> {
        val prebuilt = prebuiltDir
        if (prebuilt != null) {
            project.tasks.register<GhosttyPrebuiltTask>(name) {
                prebuiltDir.set(prebuilt.dir(name))
                this.outputDir.set(outputDir)
            }
        } else {
            val task = project.tasks.register(name, type) {
                dependsOn(source)
                this.outputDir.set(outputDir)
                configure()
            }
            rootTask<Sync>("ghosttyPrebuilt") {
                into(buildDir.map { it.dir("prebuilt") })
            }.configure {
                from(task) { into(name) }
            }
        }
        return project.tasks.named(name, GhosttyOutputTask::class.java)
    }

    private inline fun <reified T : Task> rootTask(name: String, crossinline configure: T.() -> Unit): TaskProvider<T> {
        val tasks = project.rootProject.tasks
        return if (tasks.names.contains(name)) {
            tasks.named(name, T::class.java)
        } else {
            tasks.register(name, T::class.java) { configure() }
        }
    }
}
