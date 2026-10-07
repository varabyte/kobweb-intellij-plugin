package com.varabyte.kobweb.intellij.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.varabyte.kobweb.intellij.util.idea.swing.KotlinCodeTextField
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

class KobwebSettingsConfigurable(private val project: Project)
    : BoundConfigurable(UxGlobals.FAMILY_NAME), SearchableConfigurable {
    override fun createPanel(): DialogPanel {
        val appState = KobwebAppSettingsService.getInstance().state

        fun JComponent.installCodeTooltip(code: String) {
            var activePopup: JBPopup? = null
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    if (activePopup?.isVisible == true) return

                    val codeTextField = KotlinCodeTextField(code, project).apply {
                        disposable?.let { setDisposedWith(it) }
                    }

                    activePopup = JBPopupFactory.getInstance()
                        .createComponentPopupBuilder(codeTextField, null)
                        .setRequestFocus(false)
                        .setCancelOnClickOutside(true)
                        .setCancelOnOtherWindowOpen(true)
                        .createPopup()
                        .also {
                            it.show(RelativePoint.getSouthOf(this@installCodeTooltip))
                        }
                }

                override fun mouseExited(e: MouseEvent) {
                    activePopup?.cancel()
                    activePopup = null
                }
            })
        }

        fun <T : JComponent> Cell<T>.installCodeTooltip(code: String): Cell<T> {
            component.installCodeTooltip(code)
            return this
        }

        return panel {
            group("Extract CssStyle") {
                buttonsGroup("Default CssStyle format:") {
                    row {
                        radioButton("Concise", KobwebAppSettingsService.ExtractCssStyle.Format.CONCISE)
                            .installCodeTooltip(
                                """
                                CssStyle.base {
                                    Modifier
                                }
                                """.trimIndent()
                            )
                        radioButton("Relaxed", KobwebAppSettingsService.ExtractCssStyle.Format.RELAXED)
                            .installCodeTooltip(
                                """
                                CssStyle {
                                    base {
                                        Modifier
                                    }
                                }
                                """.trimIndent()
                            )
                        radioButton("Ask me", KobwebAppSettingsService.ExtractCssStyle.Format.ASK_ME)
                    }.comment("Mouse over options to see a code example")
                }.bind(appState.extractCssStyle::format)

                buttonsGroup("Default attribute modifier strategy:") {
                    row {
                        radioButton("Leave inline", KobwebAppSettingsService.ExtractCssStyle.AttributeModifiersStrategy.INLINE)
                            .installCodeTooltip(
                                """
                                // Style
                                CssStyle.base { ... }
                                
                                // Inline
                                CssStyle.toModifier().id("hi")
                                //                    ^^^^^^^^
                                """.trimIndent()
                            )
                        radioButton("Extract", KobwebAppSettingsService.ExtractCssStyle.AttributeModifiersStrategy.EXTRACT)
                            .installCodeTooltip(
                                """
                                // Style
                                CssStyle.base(extraModifier = {
                                    Modifier.id("hi")
                                    //       ^^^^^^^^
                                }) { ... }
                                
                                // Inline
                                CssStyle.toModifier()
                                """.trimIndent()
                            )
                        radioButton("Ask me", KobwebAppSettingsService.ExtractCssStyle.AttributeModifiersStrategy.ASK_ME)
                    }.comment("Mouse over options to see a code example")
                }.bind(appState.extractCssStyle::attributeModifiersStrategy)

                row {
                    checkBox("Show \"Can't extract attribute Modifier\" warning")
                        .comment("This warning is shown when you try to extract an attribute modifier into a CssStyle that we can't because the attribute modifier is set to some value bounds to the local scope.")
                        .bindSelected(appState.extractCssStyle::showAttributeWarning)
                }
            }
        }
    }

    override fun getId() = "com.varabyte.kobweb.settings.framework"
}
