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
