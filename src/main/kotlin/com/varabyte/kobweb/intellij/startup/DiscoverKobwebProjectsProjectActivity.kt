package com.varabyte.kobweb.intellij.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.Function
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.GradleProject
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.io.File

// Constants useful for identifying external dependencies as Kobweb projects
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
 * On startup, run through all modules and dependencies, discovering which represent Kobweb projects.
 */
class DiscoverKobwebProjectsProjectActivity : ProjectActivity {
    // We originally were hoping to query a Gradle project's list of plugins to see which sort of Kobweb plugin it used;
    // however, the Gradle tooling API doesn't support this. So it's a little bit hacky, but we will just search for
    // tasks that we know are unique to each plugin type and use that to identify it. This is slightly dangerous because
    // there's no guarantee we won't delete or rename these tasks in the future, but if that happens, then we'll get
    // bug reports that the Kobweb plugin stopped working in those modules, which is not fatal.
    private object KobwebTaskNames {
        const val APPLICATION = "kobwebStart"
        const val LIBRARY =
            "kobwebGenerateLibraryMetadata" // NOTE: Older versions may use "kobwebGenerateLibraryMetadataTask"
        const val WORKER =
            "kobwebGenerateWorkerMetadata" // NOTE: Older versions may use "kobwebGenerateWorkerMetadataTask"
    }

    private fun GradleProject.findKobwebProjectType(): KobwebProject.Type? {
        return tasks.asSequence()
            .mapNotNull {
                when {
                    it.name == KobwebTaskNames.APPLICATION -> KobwebProject.Type.Application
                    it.name.startsWith(KobwebTaskNames.LIBRARY) -> KobwebProject.Type.Library
                    it.name.startsWith(KobwebTaskNames.WORKER) -> KobwebProject.Type.Worker
                    else -> null
                }
            }
            .firstOrNull()
    }

    private fun Project.queryGradleForKobwebProjects(): Set<KobwebProject> {
        val kobwebProjects = mutableSetOf<KobwebProject>()

        basePath?.let { File(it) }?.let { projectRoot ->
            val connection = GradleConnector.newConnector()
                .forProjectDirectory(projectRoot)
                .connect()

            val allProjects = mutableListOf<GradleProject>()
            connection.getModel(GradleProject::class.java)?.let { rootProject ->
                allProjects.add(rootProject)
                allProjects.addAll(rootProject.children)
            }

            allProjects.forEach { gradleProject ->
                gradleProject.findKobwebProjectType()?.let { kobwebProjectType ->
                    GradleUtil.findGradleModule(this, gradleProject.projectDirectory.path)?.let { gradleModule ->
                        kobwebProjects.add(
                            KobwebProject(
                                gradleProject.path,
                                kobwebProjectType,
                                KobwebProject.Source.Local(gradleModule)
                            )
                        )
                    }
                }
            }
        }
        return kobwebProjects
    }

    private fun Project.queryDependenciesForKobwebProjects(): Set<KobwebProject> {
        val kobwebProjects = mutableSetOf<KobwebProject>()

        val moduleManager = ModuleManager.getInstance(this)
        moduleManager.modules.forEach { module ->
            val moduleRootManager = ModuleRootManager.getInstance(module)
            moduleRootManager.orderEntries().forEachLibrary { library ->
                library?.let { lib ->
                    lib
                        .getFiles(com.intellij.openapi.roots.OrderRootType.CLASSES)
                        .filter { it.extension == "klib" }
                        .forEach { klib ->
                            val kobwebProjectType = when {
                                KOBWEB_METADATA_IDENTIFIERS_LIBRARY.any { klib.findFileByRelativePath(it) != null } -> {
                                    KobwebProject.Type.Library
                                }

                                klib.findFileByRelativePath(KOBWEB_METADATA_IDENTIFIER_WORKER) != null -> {
                                    KobwebProject.Type.Worker
                                }

                                else -> null
                            }

                            if (kobwebProjectType != null) {
                                kobwebProjects.add(
                                    KobwebProject(
                                        klib.name,
                                        kobwebProjectType,
                                        KobwebProject.Source.External(klib)
                                    )
                                )
                            }

                        }
                }
                true // Keep iterating
            }
        }

        return kobwebProjects
    }

    inner class RefreshKobwebProjectsModuleListener : ModuleListener {
        private fun Project.refreshKobwebProjects() {
            val kobwebProjects = service<KobwebProjectCacheService>()
            kobwebProjects.removeIf { it.source is KobwebProject.Source.Local }

            kobwebProjects.addAll(queryGradleForKobwebProjects())
        }

        override fun modulesAdded(project: Project, modules: MutableList<out Module>) {
            if (modules.any { it.isGradleModule }) {
                project.refreshKobwebProjects()
            }
        }

        override fun moduleRemoved(project: Project, module: Module) {
            if (project.service<KobwebProjectCacheService>()
                    .any { it.source is KobwebProject.Source.Local && it.source.module === module }
            ) {
                project.refreshKobwebProjects()
            }
        }

        override fun modulesRenamed(
            project: Project,
            modules: MutableList<out Module>,
            oldNameProvider: Function<in Module, String>
        ) {
            val kobwebProjects = project.service<KobwebProjectCacheService>()
            if (modules.any { module -> kobwebProjects.any { it.name == module.name } }) {
                project.refreshKobwebProjects()
            }
        }
    }

    inner class RefreshKobwebProjectsModuleRootListener : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
            val kobwebProjects = event.project.service<KobwebProjectCacheService>()
            kobwebProjects.removeIf { it.source is KobwebProject.Source.External }

            kobwebProjects.addAll(event.project.queryDependenciesForKobwebProjects())
        }
    }

    override suspend fun execute(project: Project) {
        val kobwebProjects = project.service<KobwebProjectCacheService>()

        kobwebProjects.addAll(project.queryGradleForKobwebProjects())
        project.messageBus.connect().subscribe(ModuleListener.TOPIC, RefreshKobwebProjectsModuleListener())

        kobwebProjects.addAll(project.queryDependenciesForKobwebProjects())
        project.messageBus.connect().subscribe(ModuleRootListener.TOPIC, RefreshKobwebProjectsModuleRootListener())
    }
}
