package com.varabyte.kobweb.intellij.wizards.style

import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.kobweb.intellij.test.util.analyzeOnEdt
import com.varabyte.kobweb.intellij.test.util.checkResultAndHighlight
import com.varabyte.kobweb.intellij.test.util.configureByTextAndHighlight
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
        myFixture.configureByTextAndHighlight("SomePage.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                M<caret>odifier.width(70.percent).color(Colors.Green)
            }
            """.trimIndent()
        )

        val ktDotExpr = myFixture.elementUnderCaret.getEntireDotQualifiedExpression()!!
        val result = ktDotExpr.createResult("MyStyle", useConciseSyntax = true)
        result.performRefactoring(myFixture.editor, ktDotExpr)

        myFixture.checkResultAndHighlight(
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent
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
        myFixture.configureByTextAndHighlight("SomePage.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                M<caret>odifier.width(70.percent).color(Colors.Green)
            }
            """.trimIndent()
        )

        val ktDotExpr = myFixture.elementUnderCaret.getEntireDotQualifiedExpression()!!
        val result = ktDotExpr.createResult("MyStyle", useConciseSyntax = false)
        result.performRefactoring(myFixture.editor, ktDotExpr)

        myFixture.checkResultAndHighlight(
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent
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

    fun testTopLevelStyleValuesCanBeExtracted() {
        myFixture.configureByTextAndHighlight("SomePage.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent

            fun produceSize() = 70.percent
            val THEME_COLOR = Colors.Green

            @Composable
            fun SomePage() {
                M<caret>odifier.width(produceSize()).color(THEME_COLOR)
            }
            """.trimIndent()
        )

        val ktDotExpr = myFixture.elementUnderCaret.getEntireDotQualifiedExpression()!!
        val result = ktDotExpr.createResult("MyStyle", useConciseSyntax = true)
        result.performRefactoring(myFixture.editor, ktDotExpr)

        myFixture.checkResultAndHighlight(
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier

            fun produceSize() = 70.percent
            val THEME_COLOR = Colors.Green

            val MyStyle = CssStyle.base {
                Modifier
                    .width(produceSize())
                    .color(THEME_COLOR)
            }

            @Composable
            fun SomePage() {
                MyStyle.toModifier()
            }
            """.trimIndent()
        )
    }

    fun testExtractStylesWithLocalBounds() {
        myFixture.configureByTextAndHighlight("SomePage.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                val w = 70.percent
                val c = Colors.Green
                
                M<caret>odifier.width(w).color(c)
            }
            """.trimIndent()
        )

        val ktDotExpr = myFixture.elementUnderCaret.getEntireDotQualifiedExpression()!!
        val result = ktDotExpr.createResult("MyStyle", useConciseSyntax = false)
        result.performRefactoring(myFixture.editor, ktDotExpr)

        myFixture.checkResultAndHighlight(
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent
            import com.varabyte.kobweb.compose.css.StyleVariable
            import com.varabyte.kobweb.compose.ui.modifiers.setVariable
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.toModifier
            import org.jetbrains.compose.web.css.CSSColorValue
            import org.jetbrains.compose.web.css.CSSNumericValue
            import org.jetbrains.compose.web.css.CSSUnitLengthOrPercentage
            
            val MyStyle_WidthVar by StyleVariable<CSSNumericValue<out CSSUnitLengthOrPercentage>>()
            val MyStyle_ColorVar by StyleVariable<CSSColorValue>()
            val MyStyle = CssStyle {
                base {
                    Modifier
                        .width(MyStyle_WidthVar.value())
                        .color(MyStyle_ColorVar.value())
                }
            }
            
            @Composable
            fun SomePage() {
                val w = 70.percent
                val c = Colors.Green
            
                MyStyle.toModifier()
                    .setVariable(MyStyle_WidthVar, w)
                    .setVariable(MyStyle_ColorVar, c)
            }
            """.trimIndent()
        )
    }

    fun testExtractAttributes() {
        myFixture.configureByTextAndHighlight("SomePage.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                M<caret>odifier.id("hi").width(50.percent).tabIndex(0)
            }
            """.trimIndent()
        )

        val ktDotExpr = myFixture.elementUnderCaret.getEntireDotQualifiedExpression()!!
        val result = ktDotExpr.createResult("MyStyle", useConciseSyntax = false, extractAttributes = true)
        result.performRefactoring(myFixture.editor, ktDotExpr)

        myFixture.checkResultAndHighlight(
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.toModifier

            val MyStyle = CssStyle(extraModifier = { Modifier.id("hi").tabIndex(0) }) {
                base {
                    Modifier.width(50.percent)
                }
            }
            
            @Composable
            fun SomePage() {
                MyStyle.toModifier()
            }
            """.trimIndent()
        )
    }

    fun testLeaveAttributesInline() {
        myFixture.configureByTextAndHighlight("SomePage.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                M<caret>odifier.id("hi").width(50.percent).tabIndex(0)
            }
            """.trimIndent()
        )

        val ktDotExpr = myFixture.elementUnderCaret.getEntireDotQualifiedExpression()!!
        val result = ktDotExpr.createResult("MyStyle", useConciseSyntax = false, extractAttributes = false)
        result.performRefactoring(myFixture.editor, ktDotExpr)

        myFixture.checkResultAndHighlight(
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.toModifier

            val MyStyle = CssStyle {
                base {
                    Modifier.width(50.percent)
                }
            }
            
            @Composable
            fun SomePage() {
                MyStyle.toModifier()
                    .id("hi")
                    .tabIndex(0)
            }
            """.trimIndent()
        )
    }

    fun testAttributesExtractedOnlyIfPossible() {
        myFixture.configureByTextAndHighlight("SomePage.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                val tabIndex = 0
                M<caret>odifier.id("hi").width(50.percent).tabIndex(tabIndex)
            }
            """.trimIndent()
        )

        val ktDotExpr = myFixture.elementUnderCaret.getEntireDotQualifiedExpression()!!
        val result = ktDotExpr.createResult("MyStyle", useConciseSyntax = false, extractAttributes = true)
        result.performRefactoring(myFixture.editor, ktDotExpr)

        myFixture.checkResultAndHighlight(
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.toModifier

            val MyStyle = CssStyle(extraModifier = { Modifier.id("hi") }) {
                base {
                    Modifier.width(50.percent)
                }
            }
            
            @Composable
            fun SomePage() {
                val tabIndex = 0
                MyStyle.toModifier().tabIndex(tabIndex)
            }
            """.trimIndent()
        )
    }

    fun testExtractModifierWithDefaultValues() {
        myFixture.configureByTextAndHighlight("SomePage.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.px

            @Composable
            fun SomePage() {
                val marginTop = 5.px
                M<caret>odifier
                    .padding(right = 10.px)
                    .margin(top = marginTop)
            }
            """.trimIndent()
        )

        val ktDotExpr = myFixture.elementUnderCaret.getEntireDotQualifiedExpression()!!
        val result = ktDotExpr.createResult("MyStyle", useConciseSyntax = true)
        result.performRefactoring(myFixture.editor, ktDotExpr)

        myFixture.checkResultAndHighlight(
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.px
            import com.varabyte.kobweb.compose.css.StyleVariable
            import com.varabyte.kobweb.compose.ui.modifiers.setVariable
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier
            import org.jetbrains.compose.web.css.CSSNumericValue
            import org.jetbrains.compose.web.css.CSSUnitLengthOrPercentage

            val MyStyle_MarginTopVar by StyleVariable<CSSNumericValue<out CSSUnitLengthOrPercentage>>()
            val MyStyle = CssStyle.base {
                Modifier
                    .padding(right = 10.px)
                    .margin(top = MyStyle_MarginTopVar.value())
            }

            @Composable
            fun SomePage() {
                val marginTop = 5.px
                MyStyle.toModifier().setVariable(MyStyle_MarginTopVar, marginTop)
            }
            """.trimIndent()
        )
    }

}