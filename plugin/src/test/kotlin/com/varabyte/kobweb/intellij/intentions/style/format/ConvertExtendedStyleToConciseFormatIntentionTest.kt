package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase

class ConvertExtendedStyleToConciseFormatIntentionTest : KobwebIntentionTestBase() {
    override val intentionName= ConvertExtendedStyleToConciseFormatIntention().text

    fun testRelaxedFormatCanBeConvertedToConcise() {
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

    fun testEmptyStyleCanBeConvertedToConcise() {
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

    fun testCannotCollapseStyleIfExtraStatementsFound() {
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
}