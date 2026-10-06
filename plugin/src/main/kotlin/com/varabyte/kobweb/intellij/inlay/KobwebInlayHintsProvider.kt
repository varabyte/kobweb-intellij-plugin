package com.varabyte.kobweb.intellij.inlay

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.settings.InlaySettingsConfigurable
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierChainingExtension
import com.varabyte.kobweb.intellij.util.psi.resolveToKtNamedFunction
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtCallExpression
import java.awt.event.MouseEvent
import javax.swing.JComponent

@Suppress("UnstableApiUsage")
class KobwebInlayHintsProvider : InlayHintsProvider<KobwebInlayHintsProvider.Settings> {
    data class Settings(
        var showAttributeHints: Boolean = true,
        var showStyleHints: Boolean = false,
    )

    override val key: SettingsKey<Settings> = SettingsKey("kobweb.modifier.hints")
    override val group: InlayGroup = InlayGroup.OTHER_GROUP
    override val name: String = "Kobweb"

    override val previewText: String = """
        val EXAMPLE_MODIFIER =
            Modifier
                .id("id")
                .tabIndex(0)
                .color(Colors.Red)
                .borderRadius(5.px)
    """.trimIndent()

    override fun createSettings(): Settings = Settings()

    // Render configuration UI in Settings > Editor > Inlay Hints
    override fun createConfigurable(settings: Settings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent {
                lateinit var panel: DialogPanel
                fun refreshPreview() {
                    panel.apply()
                    listener.settingsChanged()
                }
                panel = panel {
                    row {
                        checkBox(UxGlobals.textBundle.message("kobweb.inlay.hints.modifier.attr"))
                            .bindSelected(settings::showAttributeHints)
                            .onChanged { refreshPreview() }
                    }
                    row {
                        checkBox(UxGlobals.textBundle.message("kobweb.inlay.hints.modifier.style"))
                            .bindSelected(settings::showStyleHints)
                            .onChanged { refreshPreview() }
                    }
                }
                return panel
            }
        }
    }

    private val WebModifierType.badgeText: String? get() = when(this) {
        WebModifierType.ATTRS -> "attr"
        WebModifierType.STYLE -> "style"
        WebModifierType.UNKNOWN -> null
    }

    private fun showContextMenu(editor: Editor, event: MouseEvent, language: Language) {
        val group = DefaultActionGroup().apply {
            add(object : AnAction(UxGlobals.textBundle.message("kobweb.inlay.hints.action.configure")) {
                override fun actionPerformed(e: AnActionEvent) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        editor.project,
                        InlaySettingsConfigurable::class.java) { configurable ->
                        configurable.selectModel(language) { model ->
                            model.id == key.id
                        }
                    }
                }
            })
        }

        val popupMenu = ActionManager.getInstance()
            .createActionPopupMenu("KobwebInlayHintMenu", group)

        popupMenu.component.show(event.component, event.x, event.y)
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: Settings,
        sink: InlayHintsSink
    ): InlayHintsCollector {
        return if (file.isPhysical) {
            createActualCollector(file, editor, settings)
        } else {
            createPreviewCollector(editor, settings)
        }
    }

    private fun createPreviewCollector(
        editor: Editor,
        settings: Settings
    ): InlayHintsCollector {
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element !is KtCallExpression) return true

                val methodName = element.calleeExpression?.text ?: return true
                val badgeText = when {
                    (methodName == "id" || methodName == "tabIndex") && settings.showAttributeHints -> WebModifierType.ATTRS.badgeText
                    (methodName == "color" || methodName == "borderRadius") && settings.showStyleHints -> WebModifierType.STYLE.badgeText
                    else -> null
                } ?: return true

                val presentation = factory.inset(
                    factory.roundWithBackground(factory.text(badgeText)),
                    left = 4,
                    right = 2
                )

                sink.addInlineElement(
                    element.textRange.endOffset,
                    relatesToPrecedingText = true,
                    presentation = presentation,
                    placeAtTheEndOfLine = false
                )

                return true
            }
        }
    }

    private fun createActualCollector(
        file: PsiFile,
        editor: Editor,
        settings: Settings,
    ): FactoryInlayHintsCollector {
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element !is KtCallExpression) return true
                val webModifierType = element.webModifierType ?: return true

                val badgeText = when (webModifierType) {
                    WebModifierType.ATTRS if settings.showAttributeHints -> webModifierType.badgeText
                    WebModifierType.STYLE if settings.showStyleHints -> webModifierType.badgeText
                    else -> null
                } ?: return true

                val insetPresentation = factory.inset(
                    factory.roundWithBackground(factory.text(badgeText)),
                    left = 4,
                    right = 2
                )
                val contextualPresentation = factory.onClick(
                    insetPresentation,
                    MouseButton.Right
                ) { event, _ ->
                    showContextMenu(editor, event, file.language)
                }

                sink.addInlineElement(
                    element.textRange.endOffset,
                    relatesToPrecedingText = true,
                    presentation = contextualPresentation,
                    placeAtTheEndOfLine = false
                )

                return true
            }

            private val KtCallExpression.webModifierType: WebModifierType? get() {
                val callExpr = this
                val function = analyze(callExpr) {
                    callExpr.resolveToKtNamedFunction()
                        ?.takeIf { it.isModifierChainingExtension() }
                        ?: return null
                }
                return function.getWebModifierType()
            }
        }
    }
}
