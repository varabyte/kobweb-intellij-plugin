package com.varabyte.kobweb.intellij.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.varabyte.kobweb.intellij.model.KobwebModel
import com.varabyte.kobweb.intellij.model.gradle.tooling.KobwebModelBuilderService
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * A project resolver that extends an IntelliJ module with information about its Kobweb contents (if any).
 *
 * Note: In this case, "project" here refers to a Gradle project, not an IntelliJ project.
 */
class KobwebGradleProjectResolver : AbstractProjectResolverExtension() {
    object Keys {
        internal val KOBWEB_MODEL = Key.create(KobwebModel::class.java, 0)
    }

    // Note that the classes returned by `getExtraProjectModelClasses` and `getToolingExtensionsClasses` are potentially
    // consumed by a different JVM than the IDE one (e.g. the Gradle JVM). Therefore, they should be built separately
    // from the rest of the plugin, using an older JDK.
    override fun getExtraProjectModelClasses(): Set<Class<*>> = setOf(KobwebModel::class.java)
    override fun getToolingExtensionsClasses(): Set<Class<*>> = setOf(
        KobwebModel::class.java,
        KobwebModelBuilderService::class.java
    )

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        super.populateModuleExtraModels(gradleModule, ideModule)

        val kobwebModel = resolverCtx.getExtraProject(gradleModule, KobwebModel::class.java)
            ?: return // Kobweb model not found. No problem, it just means this module is not a Kobweb module

        ideModule.createChild(Keys.KOBWEB_MODEL, kobwebModel)
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
