package com.varabyte.kobweb.intellij.startup

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.action.RefreshAllExternalProjectsAction
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.startup.ProjectActivity
import com.varabyte.kobweb.intellij.notification.KobwebNotificationHandle
import com.varabyte.kobweb.intellij.notification.KobwebNotifier
import com.varabyte.kobweb.intellij.persist.project.KobwebModulesPersistentStateComponent
import com.varabyte.kobweb.intellij.project.findKobwebModel
import com.varabyte.kobweb.intellij.project.setKobwebModel
import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService
import com.varabyte.kobweb.intellij.util.module.toGradleModule
import com.varabyte.kobweb.intellij.util.project.hasAnyKobwebDependency


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
            val kobwebModels = project.service<KobwebModulesPersistentStateComponent>()
            if (project.hasAnyKobwebDependency()) {
                kobwebModels.initialize()
                project.modules.forEach { module ->
                    // Avoid adding models unnecessarily to nested modules (e.g. `jsMain`, `jvmMain`)
                    if (module === module.toGradleModule()) {
                        module.findKobwebModel()?.let { model ->
                            kobwebModels.addKobwebModel(module, model)
                        }
                    }
                }
            } else {
                kobwebModels.reset()
            }

            // After an import / gradle sync, let's just clear the cache, which should get automatically rebuilt
            // as users interact with their code.
            project.service<KobwebProjectCacheService>().clear()
        }
    }

    override suspend fun execute(project: Project) {
        val kobwebModels = project.service<KobwebModulesPersistentStateComponent>()

        // If any models are found persisted, it means we just loaded up after a restart. Let's set them directly here
        // instead of requiring another sync.
        kobwebModels.iterator(project).forEach { (module, model) -> module.setKobwebModel(model) }
        // It's possible that if a user opens a project with some files already opened that they can get cached as non-
        // Kobweb files. So to be safe, we clear the cache after setting the models.
        project.service<KobwebProjectCacheService>().clear()

        val refreshProjectAction = ActionManager.getInstance().getAction("ExternalSystem.RefreshAllProjects") as? RefreshAllExternalProjectsAction
        val syncRequestedNotification = if (refreshProjectAction != null && !kobwebModels.isStateValid(project) && project.hasAnyKobwebDependency()) {
            KobwebNotifier.notify(
                project,
                "The Kobweb plugin requires a one-time sync to enable functionality.",
                "Sync Project",
                NotificationType.WARNING
            ) {
                ActionUtil.invokeAction(refreshProjectAction, { dataId ->
                    when {
                        PlatformDataKeys.PROJECT.`is`(dataId) -> project
                        else -> null
                    }
                }, ActionPlaces.NOTIFICATION, null, null)
            }
        } else null

        val messageBusConnection = project.messageBus.connect()
        messageBusConnection.subscribe(
            ProjectDataImportListener.TOPIC,
            ImportListener(project, syncRequestedNotification)
        )

        messageBusConnection.subscribe(AnActionListener.TOPIC, object : AnActionListener {
            override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
                if (action === refreshProjectAction) {
                    // Dismiss the sync popup no matter how the user executed the sync action, either directly through
                    // the notification or by pressing the Gradle sync project button.
                    syncRequestedNotification?.expire()
                }
            }
        })
    }
}
