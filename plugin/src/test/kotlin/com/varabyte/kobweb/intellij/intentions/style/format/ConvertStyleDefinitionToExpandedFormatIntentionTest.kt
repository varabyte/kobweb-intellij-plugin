package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase

class ConvertStyleDefinitionToExpandedFormatIntentionTest : KobwebIntentionTestBase() {
    override val intentionName = ConvertStyleDefinitionToExpandedFormatIntention().text

    fun testConciseStyleCanBeConvertedToExpanded() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier
            import org.jetbrains.compose.web.css.percent

            val MyStyle = Css<caret>Style.base {
                Modifier
                    .width(70.percent)
                    .color(Colors.Green)
            }
            """.trimIndent(),
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier
            import org.jetbrains.compose.web.css.percent

            val MyStyle = CssStyle {
                base {
                    Modifier
                        .width(70.percent)
                        .color(Colors.Green)
                }
            }
            """.trimIndent()
        )
    }
}