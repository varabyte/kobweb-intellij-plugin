package com.varabyte.kobweb.intellij.wizards.style

import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.kobweb.intellij.test.util.analyzeOnEdt
import com.varabyte.kobweb.intellij.test.util.checkResultAndHighlight
import com.varabyte.kobweb.intellij.test.util.configureByTextAndHighlight
import com.varabyte.kobweb.intellij.test.util.elementUnderCaret
import com.varabyte.kobweb.intellij.util.psi.getEntireDotQualifiedExpression
import org.intellij.lang.annotations.Language
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

    private fun doTest(
        produceResult: KtDotQualifiedExpression.() -> ExtractCssStyleWizard.Result,
        @Language("kotlin")
        before: String,
        @Language("kotlin")
        after: String,
    ) {
        myFixture.configureByTextAndHighlight("SomePage.kt", before)

        val ktDotExpr = myFixture.elementUnderCaret.getEntireDotQualifiedExpression()!!
        val result = produceResult(ktDotExpr)
        result.performRefactoring(myFixture.editor, ktDotExpr)

        myFixture.checkResultAndHighlight(after)
    }

    fun testExtractConciseCssStyle() {
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true) },
            // Before
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
            """.trimIndent(),
            // After
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

    fun testExtractExpandedCssStyle() {
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = false) },
            // Before
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
            """.trimIndent(),
            // After
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
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true) },
            // Before
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
            """.trimIndent(),
            // After
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
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = false) },
            // Before
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
            """.trimIndent(),
            // After
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent
            import com.varabyte.kobweb.compose.css.CSSLengthOrPercentageNumericValue
            import com.varabyte.kobweb.compose.css.StyleVariable
            import com.varabyte.kobweb.compose.ui.modifiers.setVariable
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.toModifier
            import org.jetbrains.compose.web.css.CSSColorValue
            
            private val MyStyle_WidthVar by StyleVariable<CSSLengthOrPercentageNumericValue>()
            private val MyStyle_ColorVar by StyleVariable<CSSColorValue>()
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

    fun testDeterminingLocallyBoundParametersAppliesToClassMembers() {
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = false) },
            // Before
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent
            import org.jetbrains.compose.web.css.px

            abstract class MyWidgetBase {
                val height = 100.px
            }

            class MyWidget : MyWidgetBase() {
                val padding = 10.percent
                val br = 5.px

                @Composable
                fun Render() {
                    M<caret>odifier
                        .padding(padding)
                        .borderRadius(this.br)
                        .height(super.height)
                }
            }
            """.trimIndent(),
            // After
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent
            import org.jetbrains.compose.web.css.px
            import com.varabyte.kobweb.compose.css.CSSLengthNumericValue
            import com.varabyte.kobweb.compose.css.CSSLengthOrPercentageNumericValue
            import com.varabyte.kobweb.compose.css.StyleVariable
            import com.varabyte.kobweb.compose.ui.modifiers.setVariable
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.toModifier
            
            abstract class MyWidgetBase {
                val height = 100.px
            }

            private val MyStyle_PaddingTopVar by StyleVariable<CSSLengthOrPercentageNumericValue>()
            private val MyStyle_BorderRadiusVar by StyleVariable<CSSLengthNumericValue>()
            private val MyStyle_HeightVar by StyleVariable<CSSLengthOrPercentageNumericValue>()
            val MyStyle = CssStyle {
                base {
                    Modifier
                        .padding(MyStyle_PaddingTopVar.value())
                        .borderRadius(MyStyle_BorderRadiusVar.value())
                        .height(MyStyle_HeightVar.value())
                }
            }

            class MyWidget : MyWidgetBase() {
                val padding = 10.percent
                val br = 5.px

                @Composable
                fun Render() {
                    MyStyle.toModifier()
                        .setVariable(MyStyle_PaddingTopVar, padding)
                        .setVariable(MyStyle_BorderRadiusVar, this.br)
                        .setVariable(MyStyle_HeightVar, super.height)
                }
            }
            """.trimIndent()
        )
    }


    fun testExtractAttributes() {
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = false, extractAttributes = true) },
            // Before
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                M<caret>odifier.id("hi").width(50.percent).tabIndex(0)
            }
            """.trimIndent(),
            // After
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
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = false, extractAttributes = false) },
            // Before
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                M<caret>odifier.id("hi").width(50.percent).tabIndex(0)
            }
            """.trimIndent(),
            // After
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
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = false, extractAttributes = true) },
            // Before,
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
            """.trimIndent(),
            // After
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
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true) },
            // Before
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
            """.trimIndent(),
            // After
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.px
            import com.varabyte.kobweb.compose.css.CSSLengthOrPercentageNumericValue
            import com.varabyte.kobweb.compose.css.StyleVariable
            import com.varabyte.kobweb.compose.ui.modifiers.setVariable
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier

            private val MyStyle_MarginTopVar by StyleVariable<CSSLengthOrPercentageNumericValue>()
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
            """.trimIndent(),
        )
    }

    fun testExtractModifierWithVarArgValues() {
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true) },
            // Before
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*

            @Composable
            fun SomePage() {
                M<caret>odifier.fontFamily("Arial", "Helvetica", "sans-serif")
            }
            """.trimIndent(),
            // After
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier

            val MyStyle = CssStyle.base {
                Modifier.fontFamily("Arial", "Helvetica", "sans-serif")
            }

            @Composable
            fun SomePage() {
                MyStyle.toModifier()
            }
            """.trimIndent(),
        )
    }

    fun testLocallyBoundVarargStyleModifierCannotBeExtracted() {
        // NOTE: We might someday decide to support non-spread vararg parameters, as in:
        // ```
        // // Before
        // val font1 = "Arial"
        // val font2 = "Helvectica"
        // fontFamily(font1, font2)
        //
        // // After
        // private val MyStyle_FontFamily1Var by StyleProperty<String>()
        // private val MyStyle_FontFamily2Var by StyleProperty<String>()
        // ...
        //
        // MyStyle.toModifier()
        //   .setVariable(MyStyle_FontFamily1Var, font1)
        //   .setVariable(MyStyle_FontFamily2Var, font2)
        // ```
        // but that will add a fair amount of complexity to our code gen code and it's probably not something anyone
        // will actually do in practice. So for now we just refuse to extract it.
        //
        // No matter what, we will never be able to extract the spread operator version, as we can't set StyleProperty
        // to type Array<T> or whatever.

        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true) },
            // Before
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*

            @Composable
            fun SomePage() {
                val fonts = arrayOf("Arial", "Helvetica", "sans-serif")
                val willChangeLeft = "Left"
                val willChangeTop = "Top"
                Modifie<caret>r
                    .fontFamily(*fonts)
                    .willChange(willChangeLeft, willChangeTop)
            }
            """.trimIndent(),
            // After
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier

            val MyStyle = CssStyle.base {
                Modifier
            }

            @Composable
            fun SomePage() {
                val fonts = arrayOf("Arial", "Helvetica", "sans-serif")
                val willChangeLeft = "Left"
                val willChangeTop = "Top"
                MyStyle.toModifier()
                    .fontFamily(*fonts)
                    .willChange(willChangeLeft, willChangeTop)
            }
            """.trimIndent(),
        )
    }

    fun testComposableValuesCannotBeExtracted() {
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true) },
            // Before
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.px

            @get:Composable
            private val PADDING_RIGHT get() = 10.px
            
            @Composable
            private fun produceMarginTop() = 5.px

            @Composable
            fun SomePage() {
                M<caret>odifier
                    .padding(right = PADDING_RIGHT)
                    .margin(top = produceMarginTop())
            }
            """.trimIndent(),
            // After
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.px
            import com.varabyte.kobweb.compose.css.CSSLengthOrPercentageNumericValue
            import com.varabyte.kobweb.compose.css.StyleVariable
            import com.varabyte.kobweb.compose.ui.modifiers.setVariable
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier

            @get:Composable
            private val PADDING_RIGHT get() = 10.px
            
            @Composable
            private fun produceMarginTop() = 5.px

            private val MyStyle_PaddingRightVar by StyleVariable<CSSLengthOrPercentageNumericValue>()
            private val MyStyle_MarginTopVar by StyleVariable<CSSLengthOrPercentageNumericValue>()
            val MyStyle = CssStyle.base {
                Modifier
                    .padding(right = MyStyle_PaddingRightVar.value())
                    .margin(top = MyStyle_MarginTopVar.value())
            }

            @Composable
            fun SomePage() {
                MyStyle.toModifier()
                    .setVariable(MyStyle_PaddingRightVar, PADDING_RIGHT)
                    .setVariable(MyStyle_MarginTopVar, produceMarginTop())
            }
            """.trimIndent(),
        )
    }

    fun testColorModeReferencesConvertedToStyleBlockColorModeProperty() {
        // There are four ways to reference ColorMode inside a modifier chain while inside a composable context...
        // 1) Directly (ColorMode.current)
        // 2) By state value (ColorMode.currentState.value)
        // 3) Indirectly via a variable (val colorMode = ColorMode.current)
        // 4) Indirectly via state delegate (val colorMode by ColorMode.currentState)
        // Our extract logic should intercept all of them.
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true) },
            // Before
            """
            import androidx.compose.runtime.*
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.theme.colors.ColorMode
            import org.jetbrains.compose.web.css.CSSColorValue
            import org.jetbrains.compose.web.css.px

            @Composable
            fun SomePage() {
                val borderWidth = 1.px
                var colorByState by ColorMode.currentState
                val colorMode = ColorMode.current
                M<caret>odifier
                    .border(borderWidth, color = if (colorByState.isDark) Colors.White else Colors.Black)
                    .color(if (colorMode.isDark) Colors.Green else Colors.Red)
                    .backgroundColor(if (ColorMode.current.isDark) Colors.Red else Colors.Green)
                    .accentColor(if (ColorMode.currentState.value.isDark) Colors.Magenta else Colors.Purple)
            }
            """.trimIndent(),
            // After
            """
            import androidx.compose.runtime.*
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.theme.colors.ColorMode
            import org.jetbrains.compose.web.css.CSSColorValue
            import org.jetbrains.compose.web.css.px
            import com.varabyte.kobweb.compose.css.CSSLengthNumericValue
            import com.varabyte.kobweb.compose.css.StyleVariable
            import com.varabyte.kobweb.compose.ui.modifiers.setVariable
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier

            private val MyStyle_BorderWidthVar by StyleVariable<CSSLengthNumericValue>()
            val MyStyle = CssStyle.base {
                Modifier
                    .border(MyStyle_BorderWidthVar.value(), color = if (colorMode.isDark) Colors.White else Colors.Black)
                    .color(if (colorMode.isDark) Colors.Green else Colors.Red)
                    .backgroundColor(if (colorMode.isDark) Colors.Red else Colors.Green)
                    .accentColor(if (colorMode.isDark) Colors.Magenta else Colors.Purple)
            }

            @Composable
            fun SomePage() {
                val borderWidth = 1.px
                var colorByState by ColorMode.currentState
                val colorMode = ColorMode.current
                MyStyle.toModifier().setVariable(MyStyle_BorderWidthVar, borderWidth)
            }
            """.trimIndent(),
        )
    }

    // We need to be careful that we aren't too aggressive in converting colormode variables into `colorMode`.
    fun testColorModeVariableMayStillNeedToBeSetByStyleVariable() {
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true) },
            // Before
            """
            import androidx.compose.runtime.*
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.theme.colors.ColorMode
            import kotlin.random.Random
            import org.jetbrains.compose.web.css.CSSColorValue

            @Composable
            fun SomePage() {
                fun ColorMode.toColor(): CSSColorValue {
                    return when (this) {
                        ColorMode.LIGHT -> Colors.Black
                        ColorMode.DARK -> Colors.White
                    }
                }

                val color = ColorMode.current.toColor()
                val colorModeOrLight = if (Random.nextBoolean()) ColorMode.current else ColorMode.LIGHT
                M<caret>odifier
                    .color(color)
                    .backgroundColor(colorModeOrLight.toColor())
            }
            """.trimIndent(),
            // After
            """
            import androidx.compose.runtime.*
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.theme.colors.ColorMode
            import kotlin.random.Random
            import org.jetbrains.compose.web.css.CSSColorValue
            import com.varabyte.kobweb.compose.css.StyleVariable
            import com.varabyte.kobweb.compose.ui.modifiers.setVariable
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier

            private val MyStyle_ColorVar by StyleVariable<CSSColorValue>()
            private val MyStyle_BackgroundColorVar by StyleVariable<CSSColorValue>()
            val MyStyle = CssStyle.base {
                Modifier
                    .color(MyStyle_ColorVar.value())
                    .backgroundColor(MyStyle_BackgroundColorVar.value())
            }

            @Composable
            fun SomePage() {
                fun ColorMode.toColor(): CSSColorValue {
                    return when (this) {
                        ColorMode.LIGHT -> Colors.Black
                        ColorMode.DARK -> Colors.White
                    }
                }

                val color = ColorMode.current.toColor()
                val colorModeOrLight = if (Random.nextBoolean()) ColorMode.current else ColorMode.LIGHT
                MyStyle.toModifier()
                    .setVariable(MyStyle_ColorVar, color)
                    .setVariable(MyStyle_BackgroundColorVar, colorModeOrLight.toColor())
            }
            """.trimIndent(),
        )
    }

    fun testUnrelatedMutableStateValueIsNotColorMode() {
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true) },
            // Before
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.MutableState
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.CSSColorValue
    
            val activeColorState = object : MutableState<CSSColorValue> {
                override var value: CSSColorValue = Colors.Red
            }
    
            @Composable
            fun SomePage() {
                M<caret>odifier.color(activeColorState.value)
            }
            """.trimIndent(),
            // After
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.MutableState
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.CSSColorValue
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier
    
            val activeColorState = object : MutableState<CSSColorValue> {
                override var value: CSSColorValue = Colors.Red
            }
    
            val MyStyle = CssStyle.base {
                Modifier.color(activeColorState.value)
            }
    
            @Composable
            fun SomePage() {
                MyStyle.toModifier()
            }
            """.trimIndent(),
        )
    }

    fun testExtractionBringsCommentsAlong() {
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true) },
            // Before
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                M<caret>odifier
                    // Overhead comment
                    // Multiple lines
                    .width(70.percent)
                    .height(30.percent) // Trailing comment
                    // Overhead comment and...
                    .color(Colors.Green) // ... trailing comment
            }
            """.trimIndent(),
            // After
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
                    // Overhead comment
                    // Multiple lines
                    .width(70.percent)
                    .height(30.percent) // Trailing comment
                    // Overhead comment and...
                    .color(Colors.Green) // ... trailing comment
            }

            @Composable
            fun SomePage() {
                MyStyle.toModifier()
            }
            """.trimIndent(),
        )
    }

    fun testExtractionBringsCommentsAlong_OverheadCommentForcesNewlineAboveSingleModifierCase() {
        // Normally, extract CSS style logic for single modifiers uses a one-liner concise syntax, but if we have an
        // overhead comment, it forces the less concise syntax so it has a place to add the comment.

        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true) },
            // Before
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                M<caret>odifier
                    // Overhead comment
                    .width(70.percent)
            }
            """.trimIndent(),
            // After
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier

            val MyStyle = CssStyle.base {
                Modifier
                    // Overhead comment
                    .width(70.percent)
            }

            @Composable
            fun SomePage() {
                MyStyle.toModifier()
            }
            """.trimIndent(),
        )
    }

    fun testExtractionMovesCommentsForAttributeModifiers() {
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true, extractAttributes = true) },
            // Before
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                M<caret>odifier
                    // Overhead comment
                    .id("hi") // Trailing comment
                    // Overhead comment #2
                    .tabIndex(0) // Trailing comment #2
            }
            """.trimIndent(),
            // After
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier

            val MyStyle = CssStyle.base(extraModifier = {
                Modifier
                    // Overhead comment
                    .id("hi") // Trailing comment
                    // Overhead comment #2
                    .tabIndex(0) // Trailing comment #2
            }) {
                Modifier
            }

            @Composable
            fun SomePage() {
                MyStyle.toModifier()
            }
            """.trimIndent(),
        )
    }

    fun testExtractionDoesNotDeleteFinalTrailingCommentIfAssociatedModifierIsNotExtracted() {
        doTest(
            produceResult = { createResult("MyStyle", useConciseSyntax = true) },
            // Before
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                val id = "hi"
                M<caret>odifier
                    .width(70.percent) // Trailing comment moved
                    .id(id) // Trailing comment left in place
            }
            """.trimIndent(),
            // After
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.percent
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.base
            import com.varabyte.kobweb.silk.style.toModifier

            val MyStyle = CssStyle.base {
                Modifier.width(70.percent) // Trailing comment moved
            }

            @Composable
            fun SomePage() {
                val id = "hi"
                MyStyle.toModifier().id(id) // Trailing comment left in place
            }
            """.trimIndent(),
        )
    }
}