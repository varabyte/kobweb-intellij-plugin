package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase

class ConvertStyleDefinitionToConciseFormatIntentionTest : KobwebIntentionTestBase() {
    override val intentionName = ConvertStyleDefinitionToConciseFormatIntention().text

    fun testRelaxedStyleCanBeConvertedToConcise() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle
            import org.jetbrains.compose.web.css.percent

            val MyStyle = Css<caret>Style {
                base {
                    Modifier
                        .width(70.percent)
                        .color(Colors.Green)
                }
            }
            """.trimIndent(),
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import org.jetbrains.compose.web.css.percent

            val MyStyle = CssStyle.base {
                Modifier
                    .width(70.percent)
                    .color(Colors.Green)
            }
            """.trimIndent()
        )
    }

    fun testEmptyStyleCanBeConvertedToConcise() {
        // Dummy Modifier has to be added so the concise format can compile
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.silk.style.CssStyle

            val MyStyle = Css<caret>Style {
            }
            """.trimIndent(),
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base

            val MyStyle = CssStyle.base {
                Modifier
            }
            """.trimIndent()
        )
    }

    fun testCannotCollapseStyleIfExtraStatementsFound() {
        assertIntentionNotAvailable(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.selectors.hover
            import org.jetbrains.compose.web.css.percent

            val MyStyle = Css<caret>Style {
                base {
                    Modifier
                        .width(70.percent)
                        .color(Colors.Green)
                }

                hover {
                    Modifier.color(Colors.Red)
                }
            }
            """.trimIndent(),
        )
    }

}