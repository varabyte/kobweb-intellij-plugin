package com.varabyte.kobweb.intellij.model.gradle.tooling

import com.varabyte.kobweb.intellij.model.KobwebModel
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.model.WorkerBlock
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

private class KobwebModelImpl(
    override val projectType: KobwebProjectType,
    override val group: String?,
    override val pagesPackage: String?,
    override val apiPackage: String?,
    override val publicPath: String?,
    override val worker: WorkerBlock?,
) : KobwebModel

// We can't depend on the Kobweb Gradle plugin directly (since this code runs against whatever version of it the user's
// project uses), so we look things up dynamically instead.

private val Project.kobwebBlock get() = extensions.findByName("kobweb") as? ExtensionAware

/**
 * Read the value of a `Property<String>` on a Kobweb extension block, e.g. `getPropertyValue("getFqcn")`.
 */
private fun ExtensionAware.getPropertyValue(getterName: String): String? {
    return try {
        val property = javaClass.getMethod(getterName).invoke(this) as? Provider<*> ?: return null
        property.orNull as? String
    } catch (_: Exception) {
        // Shouldn't happen but no harm protecting against it just in case
        null
    }
}

/**
 * Read the `kobweb { worker { fqcn.set(...) } }` value from a worker project's build script, if set.
 */
private fun Project.findWorkerFqcn(): String? {
    val workerBlock = kobwebBlock?.extensions?.findByName("worker") as? ExtensionAware ?: return null
    return workerBlock.getPropertyValue("getFqcn")
}

/**
 * Read the `kobweb { pagesPackage.set(...) }` value from a project's build script.
 */
private fun Project.findPagesPackage(): String {
    // If we can't read it for some reason, fall back to Kobweb's default
    return kobwebBlock?.getPropertyValue("getPagesPackage") ?: KobwebModel.DEFAULT_PAGES_PACKAGE
}

/**
 * Read the `kobweb { apiPackage.set(...) }` value from a project's build script.
 */
private fun Project.findApiPackage(): String {
    // If we can't read it for some reason, fall back to Kobweb's default
    return kobwebBlock?.getPropertyValue("getApiPackage") ?: KobwebModel.DEFAULT_API_PACKAGE
}

/**
 * Read the `kobweb { publicPath.set(...) }` value from a project's build script.
 */
private fun Project.findPublicPath(): String {
    // If we can't read it for some reason, fall back to Kobweb's default
    return kobwebBlock?.getPropertyValue("getPublicPath") ?: KobwebModel.DEFAULT_PUBLIC_PATH
}

private fun Project.createWorkerBlock() = WorkerBlock(fqcn = findWorkerFqcn())

private fun Project.toKobwebModel(): KobwebModel? {
    val projectType = with(pluginManager) {
        when {
            hasPlugin("com.varabyte.kobweb.application") -> KobwebProjectType.Application
            hasPlugin("com.varabyte.kobweb.library") -> KobwebProjectType.Library
            hasPlugin("com.varabyte.kobweb.worker") -> KobwebProjectType.Worker
            else -> return null
        }
    }

    return KobwebModelImpl(
        projectType,
        group.toString(),
        findPagesPackage(),
        findApiPackage(),
        findPublicPath(),
        if (projectType == KobwebProjectType.Worker) createWorkerBlock() else null
    )
}

/**
 * A model builder that creates a [KobwebModel] for a Kobweb module.
 *
 * This service is declared in `META-INF/services`, where it will be found by the IntelliJ IDE engine and injected into
 * Gradle.
 *
 * This allows our code to run directly in Gradle, giving us access to use Gradle APIs.
 *
 * The injected code then returns a serializable class (a model) which can be fetched with an
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
