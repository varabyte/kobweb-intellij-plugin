package com.varabyte.kobweb.intellij.inspections.modifier

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.inspections.modifier.DanglingModifierInspection
import com.varabyte.kobweb.intellij.quickfixes.style.WrapModifierInBaseBlockQuickFix

class DanglingModifierInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = DanglingModifierInspection()

    fun testUnusedModifierChainIsFlagged() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*

            fun foo() {
                <warning descr="This modifier is dangling. You should instead use it or remove it.">Modifier.fillMaxWidth()</warning>
            }
            """.trimIndent()
        )
    }

    fun testUnusedModifierFunctionCallIsFlagged() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*

            private fun produceModifier(): Modifier = Modifier.fillMaxWidth()

            fun foo() {
                <warning descr="This modifier is dangling. You should instead use it or remove it.">produceModifier()</warning>
            }
            """.trimIndent()
        )
    }

    fun testUnusedModifierPropertyIsFlagged() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*

            private val MAX_WIDTH_MODIFIER = Modifier.fillMaxWidth()

            fun foo() {
                <warning descr="This modifier is dangling. You should instead use it or remove it.">MAX_WIDTH_MODIFIER</warning>
            }
            """.trimIndent()
        )
    }

    fun testModifierAsLastStatementOnBlockThatReturnsModifierIsFine() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors

            @Suppress("UNUSED_PARAMETER")
            private fun consume(block: () -> Modifier) { }
            fun foo() {
                consume {
                    <warning descr="This modifier is dangling. You should instead use it or remove it.">Modifier.color(Colors.Blue)</warning>
                    Modifier.fillMaxWidth()
                }
            }
            """.trimIndent()
        )
    }

    fun testModifierAsLastStatementOfBranchUsedAsValueIsFine() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.*

            fun produceModifier(isLarge: Boolean): Modifier {
                val modifier = if (isLarge) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.width(100.px)
                }
                return when {
                    isLarge -> {
                        modifier.padding(10.px)
                    }
                    else -> modifier
                }
            }
            """.trimIndent()
        )
    }

    fun testModifierReturnedFromGenericLambdaIsFine() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*

            private fun <T> produce(block: () -> T): T = block()
            val modifier = produce { Modifier.fillMaxWidth() }
            """.trimIndent()
        )
    }

    fun testModifierReturnedFromComposableLambdaIsFine() {
        // See also: https://github.com/varabyte/kobweb-intellij-plugin/issues/86
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.css.AlignContent
            import com.varabyte.kobweb.compose.css.TextDecorationLine
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.components.forms.ButtonStyle
            import com.varabyte.kobweb.silk.components.navigation.LinkStyle
            import com.varabyte.kobweb.silk.style.addVariant
            import com.varabyte.kobweb.silk.style.selectors.anyLink
            import com.varabyte.kobweb.silk.style.toModifier
            import org.jetbrains.compose.web.css.*

            val ButtonLinkVariant = LinkStyle.addVariant(@Composable { ButtonStyle.toModifier() }) {
                anyLink {
                    Modifier.color(Colors.White)
                        .textDecorationLine(TextDecorationLine.None)
                }
                base {
                    Modifier.padding(topBottom = 9.px, leftRight = 1.2.cssRem)
                        .height(42.px)
                        .alignContent(AlignContent.Center)
                }
            }
            """.trimIndent()
        )
    }

    fun testModifierAsLastStatementOfBranchInComposableLambdaIsFine() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.*

            @Suppress("UNUSED_PARAMETER")
            private fun consume(block: @Composable () -> Modifier) { }
            fun foo(isLarge: Boolean) {
                consume(@Composable {
                    if (isLarge) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.width(100.px)
                    }
                })
            }
            """.trimIndent()
        )
    }

    fun testModifierAsLastStatementOfComposableLambdaReturningUnitIsFlagged() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*

            @Suppress("UNUSED_PARAMETER")
            private fun consume(block: @Composable () -> Unit) { }
            fun foo() {
                consume(@Composable { <warning descr="This modifier is dangling. You should instead use it or remove it.">Modifier.fillMaxWidth()</warning> })
            }
            """.trimIndent()
        )
    }

    fun testUnusedModifierInCssStyleBlockCanBeWrapped() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle

            val MyStyle = CssStyle {
                val color = Colors.Blue
                <warning descr="This modifier is dangling. You should instead use it or remove it.">Modifier.color(color)</warning>
            }
            """.trimIndent(),
            thenFixWith<WrapModifierInBaseBlockQuickFix>(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.silk.style.CssStyle

            val MyStyle = CssStyle {
                val color = Colors.Blue
                base { Modifier.color(color) }
            }
            """.trimIndent()
            )
        )
    }
}
