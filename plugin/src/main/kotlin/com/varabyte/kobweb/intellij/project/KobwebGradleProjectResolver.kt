package com.varabyte.kobweb.intellij.project

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.varabyte.kobweb.intellij.model.DefaultKobwebModel
import com.varabyte.kobweb.intellij.model.KobwebModel
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService
import com.varabyte.kobweb.intellij.util.module.toGradleModule

private val KOBWEB_PLUGIN_IDS = mapOf(
    "com.varabyte.kobweb.application" to KobwebProjectType.Application,
    "com.varabyte.kobweb.library" to KobwebProjectType.Library,
    "com.varabyte.kobweb.worker" to KobwebProjectType.Worker,
)

private fun Module.resolveKobwebModel(projectBuildModel: ProjectBuildModel): KobwebModel? {
    return projectBuildModel
        .getModuleBuildModel(this)
        ?.appliedPlugins()
        ?.asSequence()
        ?.mapNotNull { it.name().valueAsString() }
        ?.mapNotNull(KOBWEB_PLUGIN_IDS::get)
        ?.firstOrNull()
        ?.let(::DefaultKobwebModel)
}

internal fun Project.refreshKobwebProjectCache() {
    val kobwebProjectsCache = service<KobwebProjectCacheService>()
    kobwebProjectsCache.clear()

    ApplicationManager.getApplication().executeOnPooledThread {
        val projectBuildModel = ProjectBuildModel.getOrLog(this) ?: run {
            kobwebProjectsCache.markReady()
            return@executeOnPooledThread
        }

        val kobwebProjects = modules.asSequence()
            .mapNotNull { it.toGradleModule() }
            .distinct()
            .mapNotNull { module ->
                module.resolveKobwebModel(projectBuildModel)?.let { kobwebModel ->
                    KobwebProject(
                        module.name,
                        kobwebModel.projectType,
                        KobwebProject.Source.Local(module)
                    )
                }
            }
            .toList()

        kobwebProjectsCache.addAll(kobwebProjects)
        kobwebProjectsCache.markReady()
    }
}

fun Module.findKobwebModel(): KobwebModel? {
    val gradleModule = toGradleModule() ?: return null
    return project.service<KobwebProjectCacheService>()[gradleModule]
        ?.type
        ?.let(::DefaultKobwebModel)
}
