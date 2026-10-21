package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase

class ConvertStyleToExpandedFormatIntentionTest : KobwebIntentionTestBase() {
    override val intentionFamilyName = ConvertStyleToExpandedFormatIntention().familyName

    fun testStyleDefinition_ConciseStyleCanBeConvertedToExpanded() {
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

    fun testExpandedStyle_ConciseFormatCanBeConvertedToExpanded() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.extendedByBase
            import org.jetbrains.compose.web.css.percent

            val BaseStyle = CssStyle {}
            val ExtendedStyle = BaseStyl<caret>e.extendedByBase {
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
            import com.varabyte.kobweb.silk.style.extendedBy
            import org.jetbrains.compose.web.css.percent

            val BaseStyle = CssStyle {}
            val ExtendedStyle = BaseStyle.extendedBy {
                base {
                    Modifier
                        .width(70.percent)
                        .color(Colors.Green)
                }
            }
            """.trimIndent()
        )
    }

    fun testExpandedStyle_ImportStillInUseIsKept() {
        // Only one of the styles is converted, so the other still needs `extendedByBase`
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.extendedByBase
            import org.jetbrains.compose.web.css.percent

            val BaseStyle = CssStyle {}
            val ExtendedStyle = BaseStyl<caret>e.extendedByBase {
                Modifier.width(70.percent)
            }
            val OtherExtendedStyle = BaseStyle.extendedByBase {
                Modifier.color(Colors.Green)
            }
            """.trimIndent(),
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.extendedBy
            import com.varabyte.kobweb.silk.style.extendedByBase
            import org.jetbrains.compose.web.css.percent

            val BaseStyle = CssStyle {}
            val ExtendedStyle = BaseStyle.extendedBy {
                base {
                    Modifier.width(70.percent)
                }
            }
            val OtherExtendedStyle = BaseStyle.extendedByBase {
                Modifier.color(Colors.Green)
            }
            """.trimIndent()
        )
    }

    fun testStyleVariant_ConciseFormatCanBeConvertedToExpanded() {
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

    fun testExpandedStyleVariant_ConciseFormatCanBeConvertedToExpanded() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.ComponentKind
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.addVariant
            import com.varabyte.kobweb.silk.style.extendedByBase
            import org.jetbrains.compose.web.css.percent

            sealed interface WidgetKind : ComponentKind
            val ExampleStyle = CssStyle<WidgetKind> {}
            val ExampleBaseVariant = ExampleStyle.addVariant {}
            val ExampleExtendedVariant = Exampl<caret>eBaseVariant.extendedByBase {
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
            import com.varabyte.kobweb.silk.style.extendedBy
            import org.jetbrains.compose.web.css.percent

            sealed interface WidgetKind : ComponentKind
            val ExampleStyle = CssStyle<WidgetKind> {}
            val ExampleBaseVariant = ExampleStyle.addVariant {}
            val ExampleExtendedVariant = Exampl<caret>eBaseVariant.extendedBy {
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