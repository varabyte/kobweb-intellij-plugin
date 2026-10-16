package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase

class ConvertStyleToConciseFormatIntentionTest : KobwebIntentionTestBase() {
    override val intentionFamilyName = ConvertStyleToConciseFormatIntention().familyName

    fun testStyleDefinition_ExpandedStyleCanBeConvertedToConcise() {
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

    fun testStyleDefinition_EmptyStyleCanBeConvertedToConcise() {
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

    fun testStyleDefinition_CannotCollapseStyleIfExtraStatementsFound() {
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

    fun testExtendedStyle_ExpandedFormatCanBeConvertedToConcise() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.extendedBy
            import org.jetbrains.compose.web.css.percent

            val BaseStyle = CssStyle {}
            val ExtendedStyle = Base<caret>Style.extendedBy {
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
            import com.varabyte.kobweb.silk.style.extendedBy
            import com.varabyte.kobweb.silk.style.extendedByBase
            import org.jetbrains.compose.web.css.percent

            val BaseStyle = CssStyle {}
            val ExtendedStyle = BaseStyle.extendedByBase {
                Modifier
                    .width(70.percent)
                    .color(Colors.Green)
            }
            """.trimIndent()
        )
    }

    fun testExtendedStyle_EmptyStyleCanBeConvertedToConcise() {
        // Dummy Modifier has to be added so the concise format can compile
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.extendedBy

            val BaseStyle = CssStyle {}
            val ExtendedStyle = Base<caret>Style.extendedBy {
            }
            """.trimIndent(),
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.extendedBy
            import com.varabyte.kobweb.silk.style.extendedByBase

            val BaseStyle = CssStyle {}
            val ExtendedStyle = BaseStyle.extendedByBase {
                Modifier
            }
            """.trimIndent()
        )
    }

    fun testExtendedStyle_CannotCollapseStyleIfExtraStatementsFound() {
        assertIntentionNotAvailable(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.extendedBy
            import com.varabyte.kobweb.silk.style.selectors.hover
            import org.jetbrains.compose.web.css.percent

            val BaseStyle = CssStyle {}
            val ExtendedStyle = Base<caret>Style.extendedBy {
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

    fun testExtendedStyle_QualifiedStyleReceiverCanBeConverted() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.ComponentKind
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.extendedBy
            import org.jetbrains.compose.web.css.px

            object Code {
                val LabelStyle = CssStyle {}
            }
            val LargeCodeLabelStyle = Code.Label<caret>Style.extendedBy {
                base {
                    Modifier.fontSize(20.px)
                }
            }
            """.trimIndent(),
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.ComponentKind
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.extendedBy
            import com.varabyte.kobweb.silk.style.extendedByBase
            import org.jetbrains.compose.web.css.px

            object Code {
                val LabelStyle = CssStyle {}
            }
            val LargeCodeLabelStyle = Code.Label<caret>Style.extendedByBase {
                Modifier.fontSize(20.px)
            }
            """.trimIndent()
        )
    }

    fun testStyleVariant_ExpandedFormatCanBeConvertedToConcise() {
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

    fun testStyleVariant_EmptyVariantCanBeConvertedToConcise() {
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

    fun testStyleVariant_CannotCollapseVariantIfExtraStatementsFound() {
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

    fun testStyleVariant_QualifiedStyleReceiverCanBeConverted() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.ComponentKind
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.addVariant
    
            sealed interface WidgetKind : ComponentKind
            object Theme {
                val ButtonStyle = CssStyle<WidgetKind> {}
            }
            val HoverVariant = Theme.Button<caret>Style.addVariant {
                base {
                    Modifier.color(Colors.Red)
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
    
            sealed interface WidgetKind : ComponentKind
            object Theme {
                val ButtonStyle = CssStyle<WidgetKind> {}
            }
            val HoverVariant = Theme.ButtonStyle.addVariantBase {
                Modifier.color(Colors.Red)
            }
            """.trimIndent()
        )
    }

    fun testExtendedStyleVariant_ExpandedFormatCanBeConvertedToConcise() {
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

    fun testExtendedStyleVariant_EmptyVariantCanBeConvertedToConcise() {
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

    fun testExtendedStyleVariant_CannotCollapseVariantIfExtraStatementsFound() {
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