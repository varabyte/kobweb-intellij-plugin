package com.varabyte.kobweb.intellij.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class KobwebSettingsConfigurable : BoundConfigurable("Kobweb"), SearchableConfigurable {
    override fun createPanel(): DialogPanel {
        val appState = KobwebAppSettingsService.getInstance().state

        return panel {
            group("Extract CssStyle") {
                buttonsGroup("Default CssStyle format:") {
                    row {
                        radioButton("Concise", KobwebAppSettingsService.ExtractCssStyle.Format.CONCISE)
                            .applyToComponent {
                                toolTipText =
                                    """
                                        <pre>
                                        CssStyle.base {
                                            Modifier
                                        }
                                        </pre>
                                    """.trimIndent()
                            }
                        radioButton("Relaxed", KobwebAppSettingsService.ExtractCssStyle.Format.RELAXED)
                            .applyToComponent {
                                toolTipText =
                                    """
                                    <pre>
                                    CssStyle {
                                        base {
                                            Modifier
                                        }
                                    }
                                    </pre>
                                    """.trimIndent()
                            }
                        radioButton("Ask me", KobwebAppSettingsService.ExtractCssStyle.Format.ASK_ME)
                    }
                }.bind(appState.extractCssStyle::format)

                buttonsGroup("Default attribute modifier strategy:") {
                    row {
                        radioButton("Leave inline", KobwebAppSettingsService.ExtractCssStyle.AttributeModifiersStrategy.INLINE)
                            .applyToComponent {
                                toolTipText =
                                    """
                                    <pre>
                                    // Style
                                    CssStyle.base { ... }
                                    
                                    // Inline
                                    CssStyle.toModifier().id("hi")
                                    </pre>
                                    """.trimIndent()
                            }
                        radioButton("Extract", KobwebAppSettingsService.ExtractCssStyle.AttributeModifiersStrategy.EXTRACT)
                            .applyToComponent {
                                toolTipText =
                                    """
                                    <pre>
                                    // Style
                                    CssStyle.base(extraModifier = {
                                        Modifier.id("hi")
                                    }) { ... }
                                    
                                    // Inline
                                    CssStyle.toModifier()
                                    </pre>
                                    """.trimIndent()
                            }
                        radioButton("Ask me", KobwebAppSettingsService.ExtractCssStyle.AttributeModifiersStrategy.ASK_ME)
                    }
                }.bind(appState.extractCssStyle::attributeModifiersStrategy)

                row {
                    checkBox("Show \"Can't extract attribute Modifier\" warning")
                        .comment("This warning is shown when you try to extract an attribute modifier into a CssStyle that we can't because the attribute modifier is set to some variable or method from a local scope.")
                        .bindSelected(appState.extractCssStyle::showAttributeWarning)
                }
            }
        }
    }

    override fun getId() = "com.varabyte.kobweb.settings.framework"
}
