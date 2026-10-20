package com.varabyte.kobweb.intellij.lineMarkers.icons

import com.varabyte.kobweb.intellij.iconpreviews.IconSet
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import com.varabyte.kobweb.intellij.iconpreviews.IconStyle
import com.varabyte.kobweb.intellij.lineMarkers.KobwebLineMarkerTestBase
import com.varabyte.kobweb.intellij.services.icons.IconPreviewService
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconSet
import com.varabyte.truthish.assertThat
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

class SilkIconLineMarkerProviderTest : KobwebLineMarkerTestBase() {
    override fun setUp() {
        super.setUp()

        // Stand-in outlines, so tests don't need to download real icon data. Only the names and styles matter here.
        fun icons(vararg names: String) = names.map { IconShape(it, 1f, Path2D.Float(Rectangle2D.Float(0f, 0f, 1f, 1f))) }
        IconPreviewService.instance.useIconSetForTests(
            SilkIconSet.FONT_AWESOME,
            IconSet(
                "fa",
                listOf(
                    IconStyle("far", icons("square")),
                    IconStyle("fas", icons("square", "house")),
                    IconStyle("fab", icons("github")),
                ),
            ),
            testRootDisposable,
        )
    }

    private fun isIconPreview(tooltip: String?) = tooltip?.startsWith(SilkIconSet.FONT_AWESOME.displayName) == true

    fun testGutterIconShownForIconCalls() {
        val expectedTooltips = listOf(
            "Font Awesome: square (regular)",
            "Font Awesome: square (solid)",
            "Font Awesome: house (solid)",
            "Font Awesome: github (brands)",
        )

        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.icons.fa.*

            @Composable
            fun SomePage() {
                FaSquare() $GUTTER_TAG
                FaSquare(style = IconStyle.FILLED) $GUTTER_TAG
                FaHouse() $GUTTER_TAG
                FaGithub() $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { isIconPreview(it.lineMarkerTooltip) },
            assertLineMarker = { index, marker ->
                assertThat(marker.lineMarkerTooltip).isEqualTo(expectedTooltips[index])
            }
        )
    }

    fun testGutterIconNotShownForNonIconCalls() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.silk.components.icons.fa.*

            @Composable
            fun FaSquareLookalike() {}

            @Composable
            fun SomePage() {
                FaIcon("square", Modifier) // Helper function, not an icon
                FaSquareLookalike() // Not from Kobweb
                FaUser() // Real icon, but missing from the preview data
            }
            """.trimIndent(),
            acceptLineMarker = { isIconPreview(it.lineMarkerTooltip) },
        )
    }
}
