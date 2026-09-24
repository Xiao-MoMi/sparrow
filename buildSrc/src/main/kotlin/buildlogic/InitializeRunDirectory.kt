package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

abstract class InitializeRunDirectory : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val templateDirectories: ConfigurableFileCollection

    @get:Internal
    abstract val targetDirectory: DirectoryProperty

    @TaskAction
    fun initialize() {
        val targetRoot = this.targetDirectory.get().asFile.toPath()
        for (templateDirectory in this.templateDirectories.files) {
            val templateRoot = templateDirectory.toPath()
            Files.walk(templateRoot).use { files ->
                files.filter { Files.isRegularFile(it) }.forEach { template ->
                    val target = targetRoot.resolve(templateRoot.relativize(template))
                    if (Files.notExists(target)) {
                        Files.createDirectories(target.parent)
                        Files.copy(template, target)
                    }
                }
            }
        }
    }
}
