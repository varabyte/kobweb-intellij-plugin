package com.varabyte.kobweb.intellij.services.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.util.psi.containingKlib
import org.jetbrains.kotlin.idea.base.util.module
import java.util.*

/**
 * A project service which can be queried to find all Kobweb projects in the current workspace.
 *
 * Knowing if you're inside a Kobweb project can be useful before running an inspection or action, so that we don't
 * waste time processing code that isn't relevant to Kobweb (e.g. if we're in a multiplatform project with Kobweb,
 * Android, and other types of modules).
 *
 * Note there's also an option to mark an element as explicitly NOT being part of a Kobweb project. This allows early
 * aborting on followup checks, allowing the caller to distinguish the difference between "get returns null because
 * this is not a kobweb project" vs "get returns null because we haven't put the value in the cache yet".
 *
 * @see KobwebProject
 */
interface KobwebProjectCacheService : Iterable<KobwebProject> {
    operator fun get(module: Module): KobwebProject?
    operator fun get(klib: VirtualFile): KobwebProject?
    fun isNotKobweb(module: Module) = get(module) == null
    fun isNotKobweb(klib: VirtualFile) = get(klib) == null

    fun add(project: KobwebProject)
    fun addAll(collection: Collection<KobwebProject>)
    fun markNonKobweb(element: PsiElement)
    fun clear()
}

private class KobwebProjectCacheServiceImpl : KobwebProjectCacheService {
    private val localProjects = Collections.synchronizedMap(mutableMapOf<Module, KobwebProject>())
    private val externalProjects = Collections.synchronizedMap(mutableMapOf<VirtualFile, KobwebProject>())
    private val nonKobwebProjects = Collections.synchronizedSet(mutableSetOf<Any>())

    override operator fun get(module: Module) = localProjects[module]
    override operator fun get(klib: VirtualFile) = externalProjects[klib]
    override fun add(project: KobwebProject) {
        when (project.source) {
            is KobwebProject.Source.External -> externalProjects[project.source.klib] = project
            is KobwebProject.Source.Local -> localProjects[project.source.module] = project
        }
    }

    override fun isNotKobweb(module: Module): Boolean {
        return nonKobwebProjects.contains(module)
    }

    override fun isNotKobweb(klib: VirtualFile): Boolean {
        return nonKobwebProjects.contains(klib)
    }

    override fun addAll(collection: Collection<KobwebProject>) {
        collection.forEach { add(it) }
    }

    override fun markNonKobweb(element: PsiElement) {
        nonKobwebProjects.add(element.module ?: element.containingKlib)
    }

    override fun clear() {
        externalProjects.clear()
        localProjects.clear()
    }

    override fun iterator(): Iterator<KobwebProject> {
        return (localProjects.values + externalProjects.values).iterator()
    }

    override fun toString(): String {
        return "KobwebProjects${this.iterator().asSequence().joinToString(prefix = "[", postfix = "]") { it.name }}"
    }
}
