package com.varabyte.kobweb.intellij.startup

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.action.RefreshAllExternalProjectsAction
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.startup.ProjectActivity
import com.varabyte.kobweb.intellij.notification.KobwebNotificationHandle
import com.varabyte.kobweb.intellij.notification.KobwebNotifier
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
                is LibraryOrderEntry -> orderEntry.libraryName.orEmpty().substringBefore(':') == "com.varabyte.kobweb"
                // Very rare, but if a project depends on Kobweb source directly, that counts. This is essentially for
                // the `kobweb/playground` project which devs use to test latest Kobweb on.
                is ModuleOrderEntry -> orderEntry.moduleName.substringBefore('.') == "kobweb"
                else -> false
            }
        }
}

/**
 * Actions to perform after the project has been loaded.
 */
class KobwebPostStartupProjectActivity : ProjectActivity {
    private class ImportListener(
        private val project: Project,
        private val syncRequestedNotification: KobwebNotificationHandle?
    ) : ProjectDataImportListener {
        override fun onImportStarted(projectPath: String?) {
            // If an import is kicked off in an indirect way, we should still dismiss the sync popup.
            syncRequestedNotification?.expire()
        }

        override fun onImportFinished(projectPath: String?) {
            project.kobwebPluginState = when (project.hasAnyKobwebDependency()) {
                true -> KobwebPluginState.INITIALIZED
                false -> KobwebPluginState.DISABLED
            }

            // After an import / gradle sync, let's just clear the cache, which should get automatically rebuilt
            // as users interact with their code.
            project.service<KobwebProjectCacheService>().clear()
        }
    }

    override suspend fun execute(project: Project) {
        if (project.hasAnyKobwebDependency() && project.kobwebPluginState == KobwebPluginState.DISABLED) {
            project.kobwebPluginState = KobwebPluginState.UNINITIALIZED
        }

        val refreshProjectAction = ActionManager.getInstance().getAction("ExternalSystem.RefreshAllProjects") as? RefreshAllExternalProjectsAction
        val syncRequestedNotification = if (refreshProjectAction != null && project.kobwebPluginState == KobwebPluginState.UNINITIALIZED) {
            KobwebNotifier.Builder("The Kobweb plugin requires a one-time sync to enable functionality.")
                .type(NotificationType.WARNING)
                .addAction("Sync Project") {
                    ActionUtil.invokeAction(refreshProjectAction, { dataId ->
                        when {
                            PlatformDataKeys.PROJECT.`is`(dataId) -> project
                            else -> null
                        }
                    }, ActionPlaces.NOTIFICATION, null, null)
                }
                .notify(project)
        } else null

        val messageBusConnection = project.messageBus.connect()
        messageBusConnection.subscribe(
            ProjectDataImportListener.TOPIC,
            ImportListener(project, syncRequestedNotification)
        )
    }
}
