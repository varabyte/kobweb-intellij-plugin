package com.varabyte.kobweb.intellij.util.kobweb.project

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.project.findKobwebModel
import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService
import com.varabyte.kobweb.intellij.util.psi.containingKlib
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.plugins.gradle.util.GradleUtil

// Constants useful for identifying external artifacts as Kobweb projects
private const val KOBWEB_METADATA_ROOT = "META-INF/kobweb"
private val KOBWEB_METADATA_IDENTIFIERS_LIBRARY = listOf(
    "$KOBWEB_METADATA_ROOT/library.json",
    // Legacy ways to identify a library, before library.json was introduced
    // We can remove these after a few months and/or when Kobweb hits 1.0
    "$KOBWEB_METADATA_ROOT/frontend.json",
    "$KOBWEB_METADATA_ROOT/backend.json",
)
private const val KOBWEB_METADATA_IDENTIFIER_WORKER = "$KOBWEB_METADATA_ROOT/worker.json"

/**
 * Returns the Kobweb project associated with the owning context of this element, or null if none is found.
 *
 * Kobweb project information can be associated with both local modules and third-party artifacts (e.g. maven
 * dependencies).
 *
 * The result is cached for subsequent calls.
 */
fun PsiElement.findKobwebProject(): KobwebProject? {
    val kobwebProjectsCache = project.service<KobwebProjectCacheService>()

    this.module
        ?.let { elementModule ->
            // Note that this module is likely a specific source submodule of the module we want (the one associated with
            // the Gradle build script). That is, we are probably getting "app.site.jsMain" when we want "app.site"
            @Suppress("UnstableApiUsage") // "findGradleModuleData" has been experimental for 5 years...
            GradleUtil.findGradleModuleData(elementModule)?.let { moduleDataNode ->
                GradleUtil.findGradleModule(this.project, moduleDataNode.data)
            }
        }?.let { module ->
            if (kobwebProjectsCache.isNotKobweb(module)) return null
            kobwebProjectsCache[module]?.let { return it }

            module.findKobwebModel()?.let { kobwebModel ->
                return KobwebProject(module.name, kobwebModel.projectType, KobwebProject.Source.Local(module)).also {
                    kobwebProjectsCache.add(it)
                }
            }
        }

    this.containingKlib?.let { klib ->
        if (kobwebProjectsCache.isNotKobweb(klib)) return null
        kobwebProjectsCache[klib]?.let { return it }

        val kobwebProjectType = when {
            KOBWEB_METADATA_IDENTIFIERS_LIBRARY.any { klib.findFileByRelativePath(it) != null } -> {
                KobwebProjectType.Library
            }

            klib.findFileByRelativePath(KOBWEB_METADATA_IDENTIFIER_WORKER) != null -> {
                KobwebProjectType.Worker
            }

            else -> null
        }

        if (kobwebProjectType != null) {
            return KobwebProject(
                klib.name,
                kobwebProjectType,
                KobwebProject.Source.External(klib)
            ).also { kobwebProjectsCache.add(it) }
        }
    }

    kobwebProjectsCache.markNonKobweb(this)
    return null
}
