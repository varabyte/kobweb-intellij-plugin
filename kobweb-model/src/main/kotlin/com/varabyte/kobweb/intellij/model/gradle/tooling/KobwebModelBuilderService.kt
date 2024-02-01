package com.varabyte.kobweb.intellij.model.gradle.tooling

import com.varabyte.kobweb.intellij.model.KobwebModel
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import org.gradle.api.Project
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

private class KobwebModelImpl(
    override val projectType: KobwebProjectType,
) : KobwebModel

private fun Project.toKobwebModel(): KobwebModel? {
    val projectType = with(pluginManager) {
        when {
            hasPlugin("com.varabyte.kobweb.application") -> KobwebProjectType.Application
            hasPlugin("com.varabyte.kobweb.library") -> KobwebProjectType.Library
            hasPlugin("com.varabyte.kobweb.worker") -> KobwebProjectType.Worker
            else -> return null
        }
    }

    return KobwebModelImpl(projectType)
}

/**
 * A model builder that creates a [KobwebModel] for a Kobweb module.
 *
 * This service is declared in `META-INF/services`, where it will be found by the IntelliJ IDE engine and injected into
 * Gradle.
 *
 * This allows our code to run directly in Gradle, giving us access to use Gradle APIs.
 *
 * The injected code then returns a serializable class (a model) which can be fetched with by an
 * `AbstractProjectResolverExtension` (which we implement elsewhere in this codebase).
 */
class KobwebModelBuilderService : AbstractModelBuilderService() {
    override fun canBuild(modelName: String?) = modelName == KobwebModel::class.qualifiedName

    override fun buildAll(modelName: String, project: Project, context: ModelBuilderContext): KobwebModel? {
        return project.toKobwebModel()
            ?.also {
                project.logger.info("Built Kobweb model for project ${project.displayName}, type ${it.projectType}")
            }
    }

    override fun reportErrorMessage(
        modelName: String,
        project: Project,
        context: ModelBuilderContext,
        exception: Exception
    ) {
        project.logger.warn(buildString {
            appendLine("The Kobweb IntelliJ plugin added some code that caused an unexpected error in your Gradle project (${project.displayName}). Consider filing an issue with the plugin authors at https://github.com/varabyte/kobweb-intellij-plugin/issues")
            append("Exception: ${exception.stackTraceToString()}")
        })
    }
}
