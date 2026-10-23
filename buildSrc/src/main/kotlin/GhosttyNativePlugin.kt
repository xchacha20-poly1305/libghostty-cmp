import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import org.gradle.work.NormalizeLineEndings
import java.io.File
import javax.inject.Inject

/** Base for tasks that run `zig build` in the `native/` project. */
abstract class GhosttyZigTask @Inject constructor(
    private val execOperations: ExecOperations,
) : GhosttyOutputTask() {
    @get:Input
    abstract val commit: Property<String>

    @get:Input
    abstract val zigVersion: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:NormalizeLineEndings
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val projectDir: DirectoryProperty

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    protected fun zigBuild(prefix: File, vararg args: String, environment: Map<String, String> = emptyMap()) {
        execOperations.exec {
            workingDir = projectDir.get().asFile
            environment(environment)
            // Since zig 0.17 the local cache entries are only reused while the global cache that built them
            // (compiler_rt, libc) is still there, so both live in cacheDir, which CI caches as one.
            val cache = cacheDir.get().asFile.absolutePath
            environment("ZIG_GLOBAL_CACHE_DIR", cache)
            commandLine(
                listOf("zig", "build") + args + listOf("--cache-dir", cache, "--prefix", prefix.absolutePath)
            )
        }
    }
}

/**
 * Builds the JNI library for each entry of [targets] (output subdirectory to zig target triple) into
 * `outputDir/<subdirectory>/`.
 */
@CacheableTask
abstract class GhosttyJniTask @Inject constructor(
    execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) : GhosttyZigTask(execOperations) {
    @get:Input
    abstract val targets: MapProperty<String, String>

    /** The NDK providing bionic for Android targets. */
    @get:Internal
    abstract val ndkHome: DirectoryProperty

    /** Identifies the NDK release for the build cache, where [ndkHome]'s path would differ between machines. */
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val ndkProperties: Provider<RegularFile>
        get() = ndkHome.file("source.properties")

    @TaskAction
    fun run() {
        val output = outputDir.get().asFile
        fileSystemOperations.delete { delete(output) }
        val environment = ndkHome.orNull?.let { mapOf("ANDROID_NDK_HOME" to it.asFile.absolutePath) }.orEmpty()
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
@CacheableTask
abstract class GhosttyGenKotlinTask @Inject constructor(
    execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) : GhosttyZigTask(execOperations) {
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
 * Registers `ghosttyAndroidJni`, `ghosttyDesktopJni` and `ghosttyGenKotlin` and exposes them as the
 * `ghosttyNative` extension ([GhosttyNative]); the module wires their outputs into its source sets.
 */
class GhosttyNativePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val env = GhosttyEnv(project)
        val nativeDir = project.rootProject.layout.projectDirectory.dir("native")
        val nativeSources = nativeDir.asFileTree.matching {
            exclude(".zig-cache/**", "zig-out/**", "zig-pkg/**")
        }
        val ndk = project.objects.directoryProperty()

        fun GhosttyZigTask.configure() {
            commit.set(env.commit)
            zigVersion.set(env.zigVersion)
            sources.from(nativeSources, env.patches)
            projectDir.set(nativeDir)
            cacheDir.set(env.buildDir.map { it.dir("zig-cache") })
        }

        val androidJni = env.register(
            "ghosttyAndroidJni",
            GhosttyJniTask::class.java,
            project.layout.buildDirectory.dir("ghostty-jni/android"),
        ) {
            configure()
            ndkHome.set(ndk)
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
        }

        // Laid out as jar resources: the desktop loader looks the library up by OS and architecture.
        val desktopJni = env.register(
            "ghosttyDesktopJni",
            GhosttyJniTask::class.java,
            project.layout.buildDirectory.dir("ghostty-jni/desktop"),
        ) {
            configure()
            val resourceDir = "io/github/xchacha20_poly1305/libghostty/native"
            targets.set(
                mapOf(
                    "linux-x86_64" to "x86_64-linux-gnu.2.17",
                    "linux-aarch64" to "aarch64-linux-gnu.2.17",
                    "macos-x86_64" to "x86_64-macos.11.0",
                    "macos-aarch64" to "aarch64-macos.11.0",
                    "windows-x86_64" to "x86_64-windows-gnu",
                    "windows-aarch64" to "aarch64-windows-gnu",
                ).mapKeys { (platform, _) -> "$resourceDir/$platform" }
            )
        }

        val kotlin = env.register(
            "ghosttyGenKotlin",
            GhosttyGenKotlinTask::class.java,
            project.layout.buildDirectory.dir("generated/ghostty/kotlin"),
        ) {
            configure()
        }

        project.extensions.add("ghosttyNative", GhosttyNative(ndk, androidJni, desktopJni, kotlin))
    }
}

class GhosttyNative(
    /** The NDK whose bionic sysroot the Android JNI library links against; set by the module. */
    val ndkHome: DirectoryProperty,
    /** JNI libraries laid out as `jniLibs`. */
    val androidJni: TaskProvider<GhosttyOutputTask>,
    /** JNI libraries laid out as jar resources. */
    val desktopJni: TaskProvider<GhosttyOutputTask>,
    /** Generated Kotlin sources. */
    val kotlin: TaskProvider<GhosttyOutputTask>,
)
