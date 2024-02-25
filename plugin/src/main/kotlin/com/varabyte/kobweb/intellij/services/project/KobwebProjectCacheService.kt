package com.varabyte.kobweb.intellij.services.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.util.module.toGradleModule
import com.varabyte.kobweb.intellij.util.psi.containingKlib
import org.jetbrains.kotlin.idea.base.util.module
import java.util.*

/**
 * A project service which caches [KobwebProject]s, allowing for quick lookups of whether a given element is part of one.
 *
 * Callers can fetch this service and then either register Kobweb Projects with it or query it.
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

    // This does NOT accept module / klib parameters like the `get` methods, because we need to support elements that
    // potentially don't live in either a module nor a klib.
    fun isMarkedNotKobweb(element: PsiElement): Boolean

    fun add(project: KobwebProject)
    fun addAll(collection: Collection<KobwebProject>)
    fun markNotKobweb(element: PsiElement)

    fun clear()
}

private class KobwebProjectCacheServiceImpl : KobwebProjectCacheService {
    private val localProjects = Collections.synchronizedMap(mutableMapOf<Module, KobwebProject>())
    private val externalProjects = Collections.synchronizedMap(mutableMapOf<VirtualFile, KobwebProject>())
    private val notKobwebProjects = Collections.synchronizedSet(mutableSetOf<Any>())

    override operator fun get(module: Module) = localProjects[module]
    override operator fun get(klib: VirtualFile) = externalProjects[klib]
    override fun add(project: KobwebProject) {
        when (project.source) {
            is KobwebProject.Source.External -> externalProjects[project.source.klib] = project
            is KobwebProject.Source.Local -> localProjects[project.source.module] = project
        }
    }

    // There are easily thousands of elements in a project, so it would be wasteful to store each one individually.
    // Instead, we return a container as broad as possible and store that.
    private fun PsiElement.toElementContainer(): Any = module?.toGradleModule() ?: containingKlib ?: containingFile

    override fun isMarkedNotKobweb(element: PsiElement): Boolean {
        return notKobwebProjects.contains(element.toElementContainer())
    }

    override fun addAll(collection: Collection<KobwebProject>) {
        collection.forEach { add(it) }
    }

    override fun markNotKobweb(element: PsiElement) {
        notKobwebProjects.add(element.toElementContainer())
    }

    override fun clear() {
        externalProjects.clear()
        localProjects.clear()
        notKobwebProjects.clear()
    }

    override fun iterator(): Iterator<KobwebProject> {
        return (localProjects.values + externalProjects.values).iterator()
    }

    override fun toString(): String {
        return "KobwebProjects${this.iterator().asSequence().joinToString(prefix = "[", postfix = "]") { it.name }}"
    }
}
