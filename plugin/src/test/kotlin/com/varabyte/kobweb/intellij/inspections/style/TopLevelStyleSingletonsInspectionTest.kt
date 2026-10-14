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

    fun testStyleInsideClassIsFlagged() {
        doTest(
            """
            @file:Suppress("UNUSED_VARIABLE")
            import com.varabyte.kobweb.silk.style.CssStyle
            
            class Outer {
                val <error>MyStyle</error> = CssStyle { }
            }
            """.trimIndent(),
            thenFixWith<MovePropertyToTopLevelQuickFix>(
            """
            @file:Suppress("UNUSED_VARIABLE")
            import com.varabyte.kobweb.silk.style.CssStyle
            
            val MyStyle = CssStyle { }

            class Outer {
            }
            """.trimIndent()
            )
        )
    }

    fun testStyleInsideNonTopLevelObjectIsFlagged() {
        doTest(
            """
            @file:Suppress("UNUSED_VARIABLE")
            import com.varabyte.kobweb.silk.style.CssStyle
            
            class Outer {
                object Inner {
                    val <error>MyStyle</error> = CssStyle { }
                }
            }
            """.trimIndent(),
            thenFixWith<MovePropertyToTopLevelQuickFix>(
            """
            @file:Suppress("UNUSED_VARIABLE")
            import com.varabyte.kobweb.silk.style.CssStyle
            
            val MyStyle = CssStyle { }

            class Outer {
                object Inner {
                }
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

    fun testStyleInsideCompanionObjectIsNotFlagged() {
        doTest(
            """
             @file:Suppress("UNUSED_VARIABLE")
             import com.varabyte.kobweb.silk.style.CssStyle
             class Outer {
                 companion object {
                     val MyStyle = CssStyle { }
                 }
             }
            """.trimIndent(),
        )
    }
}
