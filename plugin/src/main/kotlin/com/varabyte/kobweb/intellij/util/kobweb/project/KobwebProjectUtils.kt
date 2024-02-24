package com.varabyte.kobweb.intellij.util.kobweb.project

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
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
 * Given an IntelliJ module, return the associated module that represents the root of a Gradle project.
 *
 * Often, a module you fetch for a [PsiElement] is the one associated with a source directory, but what we often
 * actually want is its parent module. That is, instead of the module "app.site.jsMain" we want "app.site".
 *
 * If found, the module returned will be home to a Gradle build file, and you can be confident it represents the
 * root of a Gradle project.
 */
private fun Module.toGradleModule(): Module? {
    @Suppress("UnstableApiUsage") // "findGradleModuleData" has been experimental for 5 years...
    return GradleUtil.findGradleModuleData(this)?.let { moduleDataNode ->
        GradleUtil.findGradleModule(this.project, moduleDataNode.data)
    }
}

private fun Module.findKobwebProject(kobwebProjectsCache: KobwebProjectCacheService): KobwebProject? {
    val gradleModule = this.toGradleModule() ?: return null

    kobwebProjectsCache[gradleModule]?.let { return it }

    return gradleModule.findKobwebModel()?.let { kobwebModel ->
        KobwebProject(
            gradleModule.name,
            kobwebModel.projectType,
            KobwebProject.Source.Local(gradleModule)
        ).also {
            kobwebProjectsCache.add(it)
        }
    }
}

private fun VirtualFile.findKobwebProject(kobwebProjectsCache: KobwebProjectCacheService): KobwebProject? {
    require(this.extension == "klib")
    val klib = this

    kobwebProjectsCache[klib]?.let { return it }

    val kobwebProjectType = when {
        KOBWEB_METADATA_IDENTIFIERS_LIBRARY.any { this.findFileByRelativePath(it) != null } -> {
            KobwebProjectType.Library
        }

        this.findFileByRelativePath(KOBWEB_METADATA_IDENTIFIER_WORKER) != null -> {
            KobwebProjectType.Worker
        }

        else -> return null
    }

    return KobwebProject(
        klib.name,
        kobwebProjectType,
        KobwebProject.Source.External(klib)
    ).also { kobwebProjectsCache.add(it) }
}

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
    if (kobwebProjectsCache.isMarkedNotKobweb(this)) return null

    val kobwebProject =
        this.module?.findKobwebProject(kobwebProjectsCache)
            ?: this.containingKlib?.findKobwebProject(kobwebProjectsCache)

    if (kobwebProject == null) kobwebProjectsCache.markNotKobweb(this)

    return kobwebProject
}
