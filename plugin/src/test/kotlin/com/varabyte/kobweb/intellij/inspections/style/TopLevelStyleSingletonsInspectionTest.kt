package com.varabyte.kobweb.intellij.inspections.style

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.style.MovePropertyToTopLevelQuickFix

class TopLevelStyleSingletonsInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = TopLevelStyleSingletonsInspection()

    fun testStyleInsideFunctionIsFlagged() {
        doTest(
            """
             @file:Suppress("UNUSED_VARIABLE")
             import com.varabyte.kobweb.silk.style.CssStyle
             fun foo() {
                 val <error>MyStyle</error> = CssStyle { }
             }
            """.trimIndent(),

            thenFixWith<MovePropertyToTopLevelQuickFix>(
            """
             @file:Suppress("UNUSED_VARIABLE")
             import com.varabyte.kobweb.silk.style.CssStyle

             val MyStyle = CssStyle { }
             fun foo() {
             }
            """.trimIndent()
            )
        )
    }

    fun testStyleInsideObjectsIsNotFlagged() {
        doTest(
            """
             @file:Suppress("UNUSED_VARIABLE")
             import com.varabyte.kobweb.silk.style.CssStyle
             object Outer {
                 object Inner {
                     val MyStyle = CssStyle { }
                 }
             }
            """.trimIndent(),
        )
    }
}
