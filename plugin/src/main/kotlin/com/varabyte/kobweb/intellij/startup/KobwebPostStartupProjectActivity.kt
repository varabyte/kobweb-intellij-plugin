package com.varabyte.kobweb.intellij.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.startup.ProjectActivity
import com.varabyte.kobweb.intellij.project.refreshKobwebProjectCache
import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService
import com.varabyte.kobweb.intellij.util.kobweb.KobwebPluginState
import com.varabyte.kobweb.intellij.util.kobweb.kobwebPluginState

/**
 * A simple heuristic for checking if this project has code in it somewhere that depends on Kobweb.
 *
 * This is useful as users may install the Kobweb plugin for one or two of their projects, but we should stay out of the
 * way in every other kind of project.
 */
private fun Project.hasAnyKobwebDependency(): Boolean {
    return this.modules.asSequence()
        .flatMap { module -> module.rootManager.orderEntries.asSequence() }
        .any { orderEntry ->
            when (orderEntry) {
                // Most projects will indicate a dependency on Kobweb via library coordinates, e.g. `com.varabyte.kobweb:core`
                // Pre `2024.2`, libraryName looked like `com.varabyte.kobweb:kobweb-core:0.18.1`
                // As of `2024.2`, libraryName looks like `Gradle: com.varabyte.kobweb:kobweb-silk:0.18.1`
                is LibraryOrderEntry -> orderEntry.libraryName.orEmpty().contains("com.varabyte.kobweb:")
                // Very rare, but if a project depends on Kobweb source directly, that counts. This is essentially for
                // the `kobweb/playground` project which devs use to test latest Kobweb on.
                // Module name looks like "com.namespace.project.jsMain"
                is ModuleOrderEntry -> orderEntry.moduleName.substringBefore('.') == "kobweb"
                else -> false
            }
        }
}

/**
 * Actions to perform after the project has been loaded.
 */
class KobwebPostStartupProjectActivity : ProjectActivity {
    private class ImportListener(private val project: Project) : ProjectDataImportListener {
        override fun onImportFinished(projectPath: String?) {
            project.kobwebPluginState = when (project.hasAnyKobwebDependency()) {
                true -> KobwebPluginState.INITIALIZED.also { project.refreshKobwebProjectCache() }
                false -> KobwebPluginState.DISABLED.also { project.service<KobwebProjectCacheService>().clear() }
            }
        }
    }

    override suspend fun execute(project: Project) {
        project.kobwebPluginState = when (project.hasAnyKobwebDependency()) {
            true -> KobwebPluginState.INITIALIZED.also { project.refreshKobwebProjectCache() }
            false -> KobwebPluginState.DISABLED
        }

        val messageBusConnection = project.messageBus.connect()
        messageBusConnection.subscribe(
            ProjectDataImportListener.TOPIC,
            ImportListener(project)
        )
    }
}
