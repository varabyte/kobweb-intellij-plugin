package com.varabyte.kobweb.intellij.lineMarkers.icons

import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import com.varabyte.kobweb.intellij.iconpreviews.PreviewIconSet
import com.varabyte.kobweb.intellij.iconpreviews.PreviewIconStyle
import com.varabyte.kobweb.intellij.services.icons.IconCallPreviews
import com.varabyte.kobweb.intellij.services.icons.IconPreviewService
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.kobweb.intellij.test.util.analyzeOnEdt
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconSet
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconStyle
import com.varabyte.kobweb.intellij.util.kobweb.silk.SvgIconCall
import com.varabyte.kobweb.intellij.util.kobweb.silk.asSilkIconCall
import com.varabyte.kobweb.intellij.util.kobweb.silk.asSvgIconCall
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.psi.KtCallExpression
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

class SilkIconPopupTest : KobwebApplicationTestCase() {
    private fun faStyle(enumEntry: String): SilkIconStyle =
        SilkIconSet.FONT_AWESOME.styles.single { enumEntry in it.enumEntries }

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

        // Tests don't actually check the preview, but the popup needs one. So we load a stand-in placeholder.
        val square = IconShape("square", 1f, Path2D.Float(Rectangle2D.Float(0f, 0f, 1f, 1f)))
        IconPreviewService.instance.useIconSetForTests(
            SilkIconSet.FONT_AWESOME,
            PreviewIconSet(
                "fa6",
                listOf(
                    PreviewIconStyle("far", listOf(square)),
                ),
            ),
            testRootDisposable,
        )
        val previews = IconCallPreviews.find(iconCall) ?: error("No previews")
        SilkIconPopup(call, previews).setStyle(targetStyle)

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

    fun testChoosingStyleUsesRenamedEntries() {
        // A stand-in for Kobweb 0.25.2's `silk-icons-fa`, which renamed the style enum's entries. The tests depend on
        // an older version, so `style` is required here to tell this apart from the real `FaSquare` overload.
        myFixture.addFileToProject(
            "silk/FaIcons.kt",
            """
            package com.varabyte.kobweb.silk.components.icons.fa

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier

            enum class FaIconStyle { SOLID, REGULAR }

            @Composable
            fun FaSquare(style: FaIconStyle, modifier: Modifier = Modifier) {}
            """.trimIndent()
        )

        doTest(
            """
            import com.varabyte.kobweb.silk.components.icons.fa.*

            fun test() { FaSquare(style = FaIconStyle.REGULAR) }
            """.trimIndent(),
            targetStyle = faStyle("SOLID"),
            """
            import com.varabyte.kobweb.silk.components.icons.fa.*

            fun test() { FaSquare(style = FaIconStyle.SOLID) }
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

    fun testSvgIconLinksToItsSource() {
        myFixture.configureByText(
            "PopupTest.kt",
            """
            import com.varabyte.kobweb.silk.components.icons.*

            fun test() { EyeIcon() }
            """.trimIndent()
        )
        val call = PsiTreeUtil.findChildrenOfType(myFixture.file, KtCallExpression::class.java)
            .single { it.calleeExpression?.text == "EyeIcon" }

        assertThat((call.asSvgIconCall() as? SvgIconCall.SilkIcon)?.sourceUrl).isEqualTo(
            "https://github.com/varabyte/kobweb/blob/main/frontend/silk-widgets/src/jsMain/kotlin/" +
                "com/varabyte/kobweb/silk/components/icons/EyeIcon.kt"
        )
    }
}
