package com.varabyte.kobweb.intellij.wizards.style

import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.kobweb.intellij.test.util.analyzeOnEdt
import com.varabyte.kobweb.intellij.test.util.elementUnderCaret
import com.varabyte.kobweb.intellij.util.psi.getEntireDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

class ExtractCssStyleWizardResultTest : KobwebApplicationTestCase() {
    private fun KtDotQualifiedExpression.createResult(
        styleName: String,
        useConciseSyntax: Boolean,
        extractAttributes: Boolean = false,
    ): ExtractCssStyleWizard.Result {
        val ktDotExpr = this
        val modifierChainInfo = analyzeOnEdt(ktDotExpr) {
            ktDotExpr.toModifierChainInfo()
        }

        return object : ExtractCssStyleWizard.Result {
            override val styleName = styleName
            override val modifierChainInfo = modifierChainInfo
            override val extractAttributes = extractAttributes
            override val useConciseSyntax = useConciseSyntax
        }
    }

    fun testExtractConciseCssStyle() {
        myFixture.configureByText("SomePage.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors

            @Composable
            fun SomePage() {
                M<caret>odifier.width(70.percent).color(Colors.Green)
            }
            """.trimIndent()
        )

        val ktDotExpr = myFixture.elementUnderCaret.getEntireDotQualifiedExpression()!!
        val result = ktDotExpr.createResult("MyStyle", useConciseSyntax = true)
        result.performRefactoring(myFixture.editor, ktDotExpr)

        myFixture.checkResult(
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier

            val MyStyle = CssStyle.base {
                Modifier
                    .width(70.percent)
                    .color(Colors.Green)
            }

            @Composable
            fun SomePage() {
                MyStyle.toModifier()
            }
            """.trimIndent()
        )
    }

    fun testExtractRelaxedCssStyle() {
        myFixture.configureByText("SomePage.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors

            @Composable
            fun SomePage() {
                M<caret>odifier.width(70.percent).color(Colors.Green)
            }
            """.trimIndent()
        )

        val ktDotExpr = myFixture.elementUnderCaret.getEntireDotQualifiedExpression()!!
        val result = ktDotExpr.createResult("MyStyle", useConciseSyntax = false)
        result.performRefactoring(myFixture.editor, ktDotExpr)

        myFixture.checkResult(
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.toModifier

            val MyStyle = CssStyle {
                base {
                    Modifier
                        .width(70.percent)
                        .color(Colors.Green)
                }
            }

            @Composable
            fun SomePage() {
                MyStyle.toModifier()
            }
            """.trimIndent()
        )
    }


}