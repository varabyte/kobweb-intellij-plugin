package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase

class ConvertStyleVariantToConciseFormatIntentionTest : KobwebIntentionTestBase() {
    override val intentionName= ConvertStyleVariantToConciseFormatIntention().text

    fun testRelaxedFormatCanBeConvertedToConcise() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.ComponentKind
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.addVariant
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
            val ExampleVariant = ExampleStyle.addVariantBase {
                Modifier
                    .width(70.percent)
                    .color(Colors.Green)
            }
            """.trimIndent()
        )
    }

    fun testEmptyVariantCanBeConvertedToConcise() {
        // Dummy Modifier has to be added so the concise format can compile
        doTest(
            """
            import com.varabyte.kobweb.silk.style.ComponentKind
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.addVariant

            sealed interface WidgetKind : ComponentKind
            val ExampleStyle = CssStyle<WidgetKind> {}
            val ExampleVariant = Exa<caret>mpleStyle.addVariant {
            }
            """.trimIndent(),
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.silk.style.ComponentKind
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.addVariant
            import com.varabyte.kobweb.silk.style.addVariantBase

            sealed interface WidgetKind : ComponentKind
            val ExampleStyle = CssStyle<WidgetKind> {}
            val ExampleVariant = Exa<caret>mpleStyle.addVariantBase {
                Modifier
            }
            """.trimIndent()
        )
    }

    fun testCannotCollapseVariantIfExtraStatementsFound() {
        assertIntentionNotAvailable(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.ComponentKind
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.addVariant
            import com.varabyte.kobweb.silk.style.selectors.hover
            import org.jetbrains.compose.web.css.percent

            sealed interface WidgetKind : ComponentKind
            val ExampleStyle = CssStyle<WidgetKind> {}
            val ExampleVariant = Exa<caret>mpleStyle.addVariant {
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