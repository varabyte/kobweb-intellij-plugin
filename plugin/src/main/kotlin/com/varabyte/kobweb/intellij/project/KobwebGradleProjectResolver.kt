package com.varabyte.kobweb.intellij.project

import com.intellij.gradle.toolingExtension.modelProvider.GradleClassProjectModelProvider
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.module.Module
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.varabyte.kobweb.intellij.model.KobwebModel
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.model.gradle.tooling.KobwebModelBuilderService
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * A project resolver that extends an IntelliJ module with information about its Kobweb contents (if any).
 *
 * Note: In this case, "project" here refers to a Gradle project, not an IntelliJ project.
 */
class KobwebGradleProjectResolver : AbstractProjectResolverExtension() {
    companion object {
        private val logger by lazy {
            Logger.getInstance(KobwebGradleProjectResolver::class.java).apply { setLevel(LogLevel.ALL) }
        }
    }

    object Keys {
        internal val KOBWEB_MODEL = Key.create(KobwebModel::class.java, 0)
    }

    override fun getModelProviders(): List<ProjectImportModelProvider> =
        listOf(GradleClassProjectModelProvider(KobwebModel::class.java))

    override fun getToolingExtensionsClasses(): Set<Class<*>> = setOf(
        KobwebModel::class.java,
        KobwebModelBuilderService::class.java,
    )

    override fun preImportCheck() {
        logger.info("Scanning modules in project \"${resolverCtx.projectPath}\", looking for Kobweb metadata...")
    }

    @Suppress("UnstableApiUsage")
    override fun resolveFinished(projectDataNode: DataNode<ProjectData>) {
        logger.info("Finished scanning \"${resolverCtx.projectPath}\"")
    }

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        super.populateModuleExtraModels(gradleModule, ideModule)

        val kobwebModel = resolverCtx.getProjectModel(gradleModule, KobwebModel::class.java)
            ?: return

        ideModule.createChild(Keys.KOBWEB_MODEL, kobwebModel)
        logger.info("Module \"${gradleModule.name}\" is a Kobweb module [${kobwebModel.projectType}]")
    }
}

fun Module.findKobwebModel(): KobwebModel? {
    val modulePath = ExternalSystemApiUtil.getExternalProjectPath(this) ?: return null

    return ExternalSystemApiUtil
        .findModuleNode(project, GradleConstants.SYSTEM_ID, modulePath)
        ?.children
        ?.singleOrNull { it.key == KobwebGradleProjectResolver.Keys.KOBWEB_MODEL }
        ?.data as? KobwebModel
}
