package com.varabyte.kobweb.intellij.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService

/**
 * Actions to perform after the project has been loaded.
 */
class KobwebPostStartupProjectActivity : ProjectActivity {
    private class ImportListener(private val project: Project) : ProjectDataImportListener {
        override fun onImportFinished(projectPath: String?) {
            // After an import / gradle sync, let's just clear the cache, which should get automatically rebuilt
            // as users interact with their code.
            project.service<KobwebProjectCacheService>().clear()
        }
    }

    override suspend fun execute(project: Project) {
        val messageBusConnection = project.messageBus.connect()
        messageBusConnection.subscribe(ProjectDataImportListener.TOPIC, ImportListener(project))
    }
}
