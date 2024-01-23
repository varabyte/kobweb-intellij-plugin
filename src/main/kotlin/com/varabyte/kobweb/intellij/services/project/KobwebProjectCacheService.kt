package com.varabyte.kobweb.intellij.services.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.varabyte.kobweb.intellij.project.KobwebProject
import java.util.*

/**
 * A project service which can be queried to find all Kobweb projects in the current workspace.
 *
 * Knowing if you're inside a Kobweb project can be useful before running an inspection or action, so that we don't
 * waste time processing code that isn't relevant to Kobweb (e.g. if we're in a multiplatform project with Kobweb,
 * Android, and other types of modules).
 *
 * @see KobwebProject
 */
interface KobwebProjectCacheService : Iterable<KobwebProject> {
    operator fun get(module: Module): KobwebProject?
    operator fun get(jar: VirtualFile): KobwebProject?
    fun add(project: KobwebProject)
    fun addAll(collection: Collection<KobwebProject>)
    fun removeIf(filter: (KobwebProject) -> Boolean)
}

private class KobwebProjectCacheServiceImpl : KobwebProjectCacheService {
    private val localProjects = Collections.synchronizedMap(mutableMapOf<Module, KobwebProject>())
    private val externalProjects = Collections.synchronizedMap(mutableMapOf<VirtualFile, KobwebProject>())

    override operator fun get(module: Module) = localProjects[module]
    override operator fun get(jar: VirtualFile) = externalProjects[jar]
    override fun add(project: KobwebProject) {
        when (project.source) {
            is KobwebProject.Source.External -> externalProjects[project.source.jar] = project
            is KobwebProject.Source.Local -> localProjects[project.source.module] = project
        }
    }

    override fun addAll(collection: Collection<KobwebProject>) {
        collection.forEach { add(it) }
    }

    override fun removeIf(filter: (KobwebProject) -> Boolean) {
        externalProjects.values.removeIf(filter)
        localProjects.values.removeIf(filter)
    }

    override fun iterator(): Iterator<KobwebProject> {
        return (localProjects.values + externalProjects.values).iterator()
    }

    override fun toString(): String {
        return "KobwebProjects${this.iterator().asSequence().joinToString(prefix = "[", postfix = "]") { it.name }}"
    }
}
