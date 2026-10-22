package com.varabyte.kobweb.intellij.util.kobweb.project

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.project.findKobwebModel
import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService
import com.varabyte.kobweb.intellij.util.kobweb.KOBWEB_METADATA_BACKEND
import com.varabyte.kobweb.intellij.util.kobweb.KOBWEB_METADATA_FRONTEND
import com.varabyte.kobweb.intellij.util.kobweb.KOBWEB_METADATA_LIBRARY
import com.varabyte.kobweb.intellij.util.kobweb.KOBWEB_METADATA_MODULE
import com.varabyte.kobweb.intellij.util.kobweb.KOBWEB_METADATA_WORKER
import com.varabyte.kobweb.intellij.util.kobweb.PackageShortcuts
import com.varabyte.kobweb.intellij.util.kobweb.isKobwebPluginEnabled
import com.varabyte.kobweb.intellij.util.kobweb.readKobwebModuleMetadata
import com.varabyte.kobweb.intellij.util.module.toGradleModule
import com.varabyte.kobweb.intellij.util.psi.containingKlib
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.name.FqName

// Metadata files useful for identifying external artifacts as Kobweb projects
private val KOBWEB_METADATA_IDENTIFIERS_LIBRARY = listOf(
    KOBWEB_METADATA_LIBRARY,
    // Legacy ways to identify a library, before library.json was introduced
    // We can remove these after a few months and/or when Kobweb hits 1.0
    KOBWEB_METADATA_FRONTEND,
    KOBWEB_METADATA_BACKEND,
)

private fun Module.findKobwebProject(kobwebProjectsCache: KobwebProjectCacheService): KobwebProject? {
    // Lightweight tests (no Gradle) register their project into the cache directly. Production code will only ever use
    // the Gradle module to generate and then cache a KobwebProject, as below.
    kobwebProjectsCache[this]?.let { return it }

    val gradleModule = this.toGradleModule() ?: return null

    kobwebProjectsCache[gradleModule]?.let { return it }

    return gradleModule.findKobwebModel()?.let { kobwebModel ->
        KobwebProject(
            gradleModule.name,
            kobwebModel.projectType,
            KobwebProject.Source.Local(gradleModule),
            kobwebModel.group,
            kobwebModel.pagesPackage,
            kobwebModel.worker,
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

        this.findFileByRelativePath(KOBWEB_METADATA_WORKER) != null -> {
            KobwebProjectType.Worker
        }

        else -> return null
    }

    return KobwebProject(
        klib.name,
        kobwebProjectType,
        KobwebProject.Source.External(klib),
        group = klib.findFileByRelativePath(KOBWEB_METADATA_MODULE)
            ?.let { moduleFile -> Json.readKobwebModuleMetadata(moduleFile.contentsToByteArray().decodeToString()) }
            ?.group,
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
    if (!project.isKobwebPluginEnabled) return null

    val kobwebProjectsCache = project.service<KobwebProjectCacheService>()
    if (kobwebProjectsCache.isMarkedNotKobweb(this)) return null

    val kobwebProject =
        this.module?.findKobwebProject(kobwebProjectsCache)
            ?: this.containingKlib?.findKobwebProject(kobwebProjectsCache)

    if (kobwebProject == null) kobwebProjectsCache.markNotKobweb(this)

    return kobwebProject
}

/**
 * Returns the local Kobweb project associated with this module, or null if none is found.
 *
 * The result is cached for subsequent calls.
 */
fun Module.findKobwebProject(): KobwebProject? {
    if (!project.isKobwebPluginEnabled) return null

    return findKobwebProject(project.service<KobwebProjectCacheService>())
}

/**
 * The fully qualified root package of this project's pages, e.g. "com.example.pages", or null if it can't be
 * determined (e.g. it's relative but the project's group is unknown).
 */
val KobwebProject.qualifiedPagesPackage: FqName?
    get() {
        // See `PsiElement.kobwebGroup` for why we treat an empty group as unknown
        val shortcuts = PackageShortcuts(group?.takeIf { it.isNotEmpty() }?.let(::FqName))
        // The pages package is only missing if IntelliJ restored a model saved by an older version of this plugin, so
        // assume Kobweb's default until the next Gradle sync
        return shortcuts.resolve(pagesPackage ?: ".pages")
    }
