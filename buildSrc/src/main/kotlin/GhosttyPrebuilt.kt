import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * A task producing files from the ghostty source into [outputDir]. [GhosttyEnv.register] registers it either as
 * the task that builds them or as a [GhosttyPrebuiltTask].
 */
abstract class GhosttyOutputTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

/** Copies this task's output from a `ghosttyPrebuilt` export instead of building it. */
abstract class GhosttyPrebuiltTask @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
) : GhosttyOutputTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val prebuiltDir: DirectoryProperty

    @TaskAction
    fun run() {
        fileSystemOperations.sync {
            from(prebuiltDir)
            into(outputDir)
        }
    }
}
