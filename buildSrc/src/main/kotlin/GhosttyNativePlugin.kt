import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations

/** Base for tasks that run `zig build` in the `native/` project. */
abstract class GhosttyZigTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Input
    abstract val commit: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val projectDir: DirectoryProperty

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    protected fun zigBuild(prefix: File, vararg args: String, environment: Map<String, String> = emptyMap()) {
        execOperations.exec {
            workingDir = projectDir.get().asFile
            environment(environment)
            commandLine(
                listOf("zig", "build") + args +
                    listOf("--cache-dir", cacheDir.get().asFile.absolutePath, "--prefix", prefix.absolutePath)
            )
        }
    }
}

/**
 * Builds the JNI library for each entry of [targets] (output subdirectory to zig target triple) into
 * `outputDir/<subdirectory>/`.
 */
abstract class GhosttyJniTask @Inject constructor(
    execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) : GhosttyZigTask(execOperations) {
    @get:Input
    abstract val targets: MapProperty<String, String>

    /** The NDK providing bionic for Android targets. */
    @get:Optional
    @get:Input
    abstract val ndkHome: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val output = outputDir.get().asFile
        fileSystemOperations.delete { delete(output) }
        val environment = ndkHome.orNull?.let { mapOf("ANDROID_NDK_HOME" to it) }.orEmpty()
        for ((subdirectory, target) in targets.get()) {
            val prefix = File(temporaryDir, subdirectory)
            fileSystemOperations.delete { delete(prefix) }
            zigBuild(prefix, "-Dtarget=$target", "-Doptimize=ReleaseSmall", environment = environment)
            // Shared libraries land in lib/ except Windows DLLs, which zig puts in bin/.
            fileSystemOperations.copy {
                from(File(prefix, "lib"), File(prefix, "bin"))
                include("*.so", "*.dylib", "*.dll")
                into(File(output, subdirectory))
            }
        }
    }
}

/** Generates the Kotlin mirrors of ghostty's input types (`zig build gen-kotlin`). */
abstract class GhosttyGenKotlinTask @Inject constructor(
    execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) : GhosttyZigTask(execOperations) {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val prefix = temporaryDir
        fileSystemOperations.delete { delete(prefix) }
        zigBuild(prefix, "gen-kotlin")
        val output = outputDir.get().asFile
        fileSystemOperations.sync {
            from(File(prefix, "kotlin"))
            into(output)
        }
    }
}

/**
 * Registers `ghosttyAndroidJni`, `ghosttyDesktopJni` and `ghosttyGenKotlin`; the module wires their outputs into
 * its source sets.
 */
class GhosttyNativePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val env = GhosttyEnv(project)
        val nativeDir = project.rootProject.layout.projectDirectory.dir("native")
        val nativeSources = nativeDir.asFileTree.matching {
            exclude(".zig-cache/**", "zig-out/**", "zig-pkg/**")
        }

        fun GhosttyZigTask.configure() {
            dependsOn(env.source)
            commit.set(env.commit)
            sources.from(nativeSources, env.patches)
            projectDir.set(nativeDir)
            cacheDir.set(env.buildDir.map { it.dir("zig-cache") })
        }

        project.tasks.register<GhosttyJniTask>("ghosttyAndroidJni") {
            configure()
            // Zig defaults the Android API level to 29 when the target omits it
            // (std.Target.Os.VersionRange.default).
            targets.set(
                mapOf(
                    "arm64-v8a" to "aarch64-linux-android.21",
                    "armeabi-v7a" to "arm-linux-androideabi.21",
                    "x86_64" to "x86_64-linux-android.21",
                    "x86" to "x86-linux-android.21",
                )
            )
            outputDir.set(project.layout.buildDirectory.dir("ghostty-jni/android"))
        }

        // Laid out as jar resources: the desktop loader looks the library up by OS and architecture.
        project.tasks.register<GhosttyJniTask>("ghosttyDesktopJni") {
            configure()
            val resourceDir = "io/github/xchacha20_poly1305/libghostty/native"
            targets.set(
                mapOf(
                    "linux-x86_64" to "x86_64-linux-gnu.2.28",
                    "linux-aarch64" to "aarch64-linux-gnu.2.28",
                    "macos-x86_64" to "x86_64-macos.11.0",
                    "macos-aarch64" to "aarch64-macos.11.0",
                    "windows-x86_64" to "x86_64-windows-gnu",
                    "windows-aarch64" to "aarch64-windows-gnu",
                ).mapKeys { (platform, _) -> "$resourceDir/$platform" }
            )
            outputDir.set(project.layout.buildDirectory.dir("ghostty-jni/desktop"))
        }

        project.tasks.register<GhosttyGenKotlinTask>("ghosttyGenKotlin") {
            configure()
            outputDir.set(project.layout.buildDirectory.dir("generated/ghostty/kotlin"))
        }
    }
}
