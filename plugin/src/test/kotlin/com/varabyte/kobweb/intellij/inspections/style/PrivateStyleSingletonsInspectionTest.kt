package com.varabyte.kobweb.intellij.inspections.style

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.AddSuppressionQuickFix
import com.varabyte.kobweb.intellij.quickfixes.AddUnderscorePrefixQuickFix
import com.varabyte.kobweb.intellij.quickfixes.MakePublicQuickFix

class PrivateStyleSingletonsInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = PrivateStyleSingletonsInspection()

    fun testUnderscorePrefixedStyleIsNotFlagged() {
        doTest(
            """
                 import com.varabyte.kobweb.silk.style.CssStyle
                 private val _MyStyle = CssStyle { }
             """.trimIndent()
        )
    }

    fun testSuppressedStyleIsNotFlagged() {
        doTest(
            """
                 import com.varabyte.kobweb.silk.style.CssStyle
                 @Suppress("PRIVATE_CSS_STYLE")
                 private val MyStyle = CssStyle { }
             """.trimIndent()
        )
    }

    fun testPrivateStyleIsFlaggedAndCanBeMadePublic() {
         doTest(
             """
             import com.varabyte.kobweb.silk.style.CssStyle
             <error>private</error> val MyStyle = CssStyle { }
             """.trimIndent(),
             thenFixWith<MakePublicQuickFix>(
                 """
                 import com.varabyte.kobweb.silk.style.CssStyle
                 val MyStyle = CssStyle { }
                """.trimIndent()
             )
         )
     }

    fun testPrivateStyleIsFlaggedAndCanBePrefixedWithAnUnderscores() {
         doTest(
             """
             import com.varabyte.kobweb.silk.style.CssStyle
             <error>private</error> val MyStyle = CssStyle { }
             """.trimIndent(),
             thenFixWith<AddUnderscorePrefixQuickFix>(
                 """
                 import com.varabyte.kobweb.silk.style.CssStyle
                 private val _MyStyle = CssStyle { }
                """.trimIndent()
             )
         )
     }

    fun testPrivateStyleIsFlaggedAndCanBeSuppressed() {
         doTest(
             """
             import com.varabyte.kobweb.silk.style.CssStyle
             <error>private</error> val MyStyle = CssStyle { }
             """.trimIndent(),
             thenFixWith<AddSuppressionQuickFix>(
                 """
                 import com.varabyte.kobweb.silk.style.CssStyle
                 @Suppress("PRIVATE_CSS_STYLE")
                 private val MyStyle = CssStyle { }
                """.trimIndent()
             )
         )
     }

     fun testPrivateVariantIsFlagged() {
         doTest(
             """
             import com.varabyte.kobweb.silk.style.*
             sealed interface WidgetKind : ComponentKind
             val WidgetStyle = CssStyle<WidgetKind> {}
             <error>private</error> val WidgetVariant = WidgetStyle.addVariant {}
             """.trimIndent(),
         )
     }

     fun testUnderscorePrefixedVariantIsNotFlagged() {
         doTest(
             """
             import com.varabyte.kobweb.silk.style.*
             sealed interface WidgetKind : ComponentKind
             val WidgetStyle = CssStyle<WidgetKind> {}
             private val _WidgetVariant = WidgetStyle.addVariant {}
             """.trimIndent()
         )
     }

     fun testSuppressedVariantIsNotFlagged() {
         doTest(
             """
             import com.varabyte.kobweb.silk.style.*
             sealed interface WidgetKind : ComponentKind
             val WidgetStyle = CssStyle<WidgetKind> {}
             @Suppress("PRIVATE_CSS_STYLE_VARIANT")
             private val WidgetVariant = WidgetStyle.addVariant {}
             """.trimIndent()
         )
     }

     fun testPrivateKeyframesIsFlagged() {
         doTest(
             """
             import com.varabyte.kobweb.silk.style.animation.Keyframes
             <error>private</error> val MyKeyframes = Keyframes {}
             """.trimIndent(),
         )
     }

     fun testUnderscorePrefixedKeyframesIsNotFlagged() {
         doTest(
             """
             import com.varabyte.kobweb.silk.style.animation.Keyframes
             private val _MyKeyframes = Keyframes {}
             """.trimIndent()
         )
     }

     fun testSuppressedKeyframesIsNotFlagged() {
         doTest(
             """
             import com.varabyte.kobweb.silk.style.animation.Keyframes
             @Suppress("PRIVATE_KEYFRAMES")
             private val _MyKeyframes = Keyframes {}
             """.trimIndent()
         )
     }
 }
