package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase

class ConvertExtendedStyleToRelaxedFormatIntentionTest : KobwebIntentionTestBase() {
    override val intentionName: String = ConvertExtendedStyleToRelaxedFormatIntention().text

    fun testConciseFormatCanBeConvertedToRelaxed() {
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
            import com.varabyte.kobweb.silk.style.extendedByBase
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
}