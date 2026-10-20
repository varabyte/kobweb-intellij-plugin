package com.varabyte.kobweb.intellij.lineMarkers.icons

import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import com.varabyte.kobweb.intellij.services.icons.IconPreview
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.kobweb.intellij.test.util.analyzeOnEdt
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconSet
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconStyle
import com.varabyte.kobweb.intellij.util.kobweb.silk.asSilkIconCall
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.psi.KtCallExpression
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

class SilkIconPopupTest : KobwebApplicationTestCase() {
    private fun faStyle(enumEntry: String): SilkIconStyle =
        SilkIconSet.FONT_AWESOME.styles.single { it.enumEntry == enumEntry }

    /** Choose [targetStyle] (or "Default", if null) for the `FaSquare` call in [code], and check the result. */
    private fun doTest(
        @Language("kotlin") code: String,
        targetStyle: SilkIconStyle?,
        @Language("kotlin") expected: String,
    ) {
        myFixture.configureByText("PopupTest.kt", code)
        val call = PsiTreeUtil.findChildrenOfType(myFixture.file, KtCallExpression::class.java)
            .single { it.calleeExpression?.text == "FaSquare" }
        val iconCall = analyzeOnEdt(call) { call.asSilkIconCall() } ?: error("Not an icon call")

        // Tests don't actually check this, but the popup needs at least one. So we create a stand-in placeholder.
        val preview = IconPreview(
            IconShape("square", 1f, Path2D.Float(Rectangle2D.Float(0f, 0f, 1f, 1f))),
            SilkIconSet.FONT_AWESOME,
            faStyle("OUTLINE"),
        )
        SilkIconPopup(call, iconCall, listOf(preview)).setStyle(targetStyle)

        myFixture.checkResult(expected)
    }

    fun testChoosingStyleAddsArgument() {
        doTest(
            """
            import com.varabyte.kobweb.silk.components.icons.fa.*

            fun test() { FaSquare() }
            """.trimIndent(),
            targetStyle = faStyle("FILLED"),
            """
            import com.varabyte.kobweb.silk.components.icons.fa.*

            fun test() { FaSquare(style = IconStyle.FILLED) }
            """.trimIndent(),
        )
    }

    fun testChoosingStyleAddsImportIfNeeded() {
        doTest(
            """
            import com.varabyte.kobweb.silk.components.icons.fa.FaSquare

            fun test() { FaSquare() }
            """.trimIndent(),
            targetStyle = faStyle("FILLED"),
            """
            import com.varabyte.kobweb.silk.components.icons.fa.FaSquare
            import com.varabyte.kobweb.silk.components.icons.fa.IconStyle

            fun test() { FaSquare(style = IconStyle.FILLED) }
            """.trimIndent(),
        )
    }

    fun testChoosingStyleReplacesArgument() {
        doTest(
            """
            import com.varabyte.kobweb.silk.components.icons.fa.*

            fun test() { FaSquare(style = IconStyle.FILLED) }
            """.trimIndent(),
            targetStyle = faStyle("OUTLINE"),
            """
            import com.varabyte.kobweb.silk.components.icons.fa.*

            fun test() { FaSquare(style = IconStyle.OUTLINE) }
            """.trimIndent(),
        )
    }

    fun testChoosingStyleReplacesPositionalArgument() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.silk.components.icons.fa.*

            fun test() { FaSquare(Modifier, IconStyle.FILLED) }
            """.trimIndent(),
            targetStyle = faStyle("OUTLINE"),
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.silk.components.icons.fa.*

            fun test() { FaSquare(Modifier, IconStyle.OUTLINE) }
            """.trimIndent(),
        )
    }

    fun testChoosingDefaultRemovesArgument() {
        doTest(
            """
            import com.varabyte.kobweb.silk.components.icons.fa.*

            fun test() { FaSquare(style = IconStyle.FILLED) }
            """.trimIndent(),
            targetStyle = null,
            """
            import com.varabyte.kobweb.silk.components.icons.fa.*

            fun test() { FaSquare() }
            """.trimIndent(),
        )
    }

    fun testChoosingDefaultNamesFollowingArguments() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.silk.components.icons.fa.*

            fun test() { FaSquare(Modifier, IconStyle.FILLED, IconSize.LG) }
            """.trimIndent(),
            targetStyle = null,
            """
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.silk.components.icons.fa.*

            fun test() { FaSquare(Modifier, size = IconSize.LG) }
            """.trimIndent(),
        )
    }
}
