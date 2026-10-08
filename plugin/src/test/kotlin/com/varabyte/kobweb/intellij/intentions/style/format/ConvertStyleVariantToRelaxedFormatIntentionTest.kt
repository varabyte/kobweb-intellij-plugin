package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase

class ConvertStyleVariantToRelaxedFormatIntentionTest : KobwebIntentionTestBase() {
    override val intentionName: String = ConvertStyleVariantToRelaxedFormatIntention().text

    fun testConciseFormatCanBeConvertedToRelaxed() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.ComponentKind
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.addVariantBase
            import org.jetbrains.compose.web.css.percent

            sealed interface WidgetKind : ComponentKind
            val ExampleStyle = CssStyle<WidgetKind> {}
            val ExampleVariant = Exa<caret>mpleStyle.addVariantBase {
                Modifier
                    .width(70.percent)
                    .color(Colors.Green)
            }
            """.trimIndent(),
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.ComponentKind
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.addVariant
            import com.varabyte.kobweb.silk.style.addVariantBase
            import org.jetbrains.compose.web.css.percent

            sealed interface WidgetKind : ComponentKind
            val ExampleStyle = CssStyle<WidgetKind> {}
            val ExampleVariant = Exa<caret>mpleStyle.addVariant {
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