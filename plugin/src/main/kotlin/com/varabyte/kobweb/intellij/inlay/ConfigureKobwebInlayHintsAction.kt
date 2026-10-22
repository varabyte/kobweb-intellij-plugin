package com.varabyte.kobweb.intellij.inlay

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.settings.showInlaySettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.kotlin.idea.KotlinLanguage

// Providers whose hints get this action in their menu
private val SUPPORTED_PROVIDER_IDS = setOf(
    KobwebAnnotationInlayHintsProvider.PROVIDER_ID,
    KobwebPageRouteInlayHintsProvider.PROVIDER_ID,
)

/**
 * An action in the menu of our inlay hints, which opens the settings for the kind of hint that was clicked on.
 *
 * Declarative inlay hints only get a "Disable" action in their menu by default, so we add this one ourselves.
 */
class ConfigureKobwebInlayHintsAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(InlayHintsProvider.PROVIDER_ID) in SUPPORTED_PROVIDER_IDS
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val providerId = e.getData(InlayHintsProvider.PROVIDER_ID) ?: return
        showInlaySettings(project, KotlinLanguage.INSTANCE) { model -> model.id == providerId }
    }
}
