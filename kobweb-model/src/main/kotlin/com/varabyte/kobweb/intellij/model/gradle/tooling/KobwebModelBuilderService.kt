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
    val type = with(pluginManager) {
        when {
            hasPlugin("com.varabyte.kobweb.application") -> KobwebProjectType.Application
            hasPlugin("com.varabyte.kobweb.library") -> KobwebProjectType.Library
            hasPlugin("com.varabyte.kobweb.worker") -> KobwebProjectType.Worker
            else -> return null
        }
    }

    return KobwebModelImpl(type)
}

/**
 * A model builder that creates a [KobwebModel] for a Kobweb module.
 *
 * Note that this class is run in the user's Gradle JVM, so it should be thought of more as Gradle code than
 * IntelliJ plugin code.
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
        project.logger.error(buildString {
            appendLine("Building the Kobweb model has failed (Project: ${project.displayName}):")
            append(exception.stackTraceToString())
        })
    }
}