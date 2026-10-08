package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase

class ConvertExtendedStyleVariantToConciseFormatIntentionTest : KobwebIntentionTestBase() {
    override val intentionName= ConvertExtendedStyleVariantToConciseFormatIntention().text

    fun testRelaxedFormatCanBeConvertedToConcise() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.ComponentKind
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.addVariant
            import com.varabyte.kobweb.silk.style.extendedBy
            import org.jetbrains.compose.web.css.percent

            sealed interface WidgetKind : ComponentKind
            val ExampleStyle = CssStyle<WidgetKind> {}
            val ExampleBaseVariant = ExampleStyle.addVariant {}
            val ExampleExtendedVariant = ExampleB<caret>aseVariant.extendedBy {
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
            import com.varabyte.kobweb.silk.style.extendedBy
            import com.varabyte.kobweb.silk.style.extendedByBase
            import org.jetbrains.compose.web.css.percent

            sealed interface WidgetKind : ComponentKind
            val ExampleStyle = CssStyle<WidgetKind> {}
            val ExampleBaseVariant = ExampleStyle.addVariant {}
            val ExampleExtendedVariant = ExampleBaseVariant.extendedByBase {
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
            import com.varabyte.kobweb.silk.style.extendedBy

            sealed interface WidgetKind : ComponentKind
            val ExampleStyle = CssStyle<WidgetKind> {}
            val ExampleBaseVariant = ExampleStyle.addVariant {}
            val ExampleExtendedVariant = ExampleBaseVar<caret>iant.extendedBy {}
            """.trimIndent(),
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.silk.style.ComponentKind
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.addVariant
            import com.varabyte.kobweb.silk.style.extendedBy
            import com.varabyte.kobweb.silk.style.extendedByBase

            sealed interface WidgetKind : ComponentKind
            val ExampleStyle = CssStyle<WidgetKind> {}
            val ExampleBaseVariant = ExampleStyle.addVariant {}
            val ExampleExtendedVariant = ExampleBaseVariant.extendedByBase {
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
            import com.varabyte.kobweb.silk.style.extendedBy
            import com.varabyte.kobweb.silk.style.selectors.hover
            import org.jetbrains.compose.web.css.percent

            sealed interface WidgetKind : ComponentKind
            val ExampleStyle = CssStyle<WidgetKind> {}
            val ExampleBaseVariant = ExampleStyle.addVariant {}
            val ExampleExtendedVariant = ExampleBaseVa<caret>riant.extendedBy {
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