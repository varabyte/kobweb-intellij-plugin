@file:Suppress("FunctionName")

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
            "Font Awesome: square (outline)",
            "Font Awesome: square (filled)",
            "Font Awesome: house",
            "Font Awesome: github",
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
                FaUser() // Real icon, but missing from the preview (test) data
            }
            """.trimIndent(),
            acceptLineMarker = { isIconPreview(it.lineMarkerTooltip) },
        )
    }

    fun testGutterIconShownForSvgIconCalls() {
        val expectedTooltips = listOf(
            "Icon: ArrowBack",
            "Icon: CheckCircle",
            "Icon: Hamburger",
            "Icon: Indeterminate",
        )

        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun SomePage() {
                ArrowBackIcon() $GUTTER_TAG
                CheckCircleIcon() $GUTTER_TAG
                HamburgerIcon() $GUTTER_TAG
                IndeterminateIcon() $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
            assertLineMarker = { index, marker ->
                assertThat(marker.lineMarkerTooltip).isEqualTo(expectedTooltips[index])
            }
        )
    }

    fun testGutterIconShownForCreateIconCalls() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.toAttrs
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun SmileyIcon(modifier: Modifier = Modifier) {
                createIcon(renderStyle = IconRenderStyle.Stroke(2), attrs = modifier.toAttrs()) { $GUTTER_TAG
                    Circle {
                        cx(12)
                        cy(12)
                        r(10)
                    }
                    Path {
                        strokeLineCap(SVGStrokeLineCap.Round)
                        d("M8 14a4 4 0 0 0 8 0")
                    }
                }
            }

            @Composable
            fun SomePage() {
                createIcon { Path { d("M4 4h16v16H4z") } } $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip == "Icon preview" },
        )
    }

    fun testGutterIconNotShownForUnreadableCreateIconCalls() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun MaybeFilledIcon(filled: Boolean) {
                createIcon(renderStyle = IconRenderStyle.Fill()) {
                    if (filled) Path { d("M4 4h16v16H4z") }
                }
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip == "Icon preview" },
        )
    }

    fun testGutterIconNotShownForOtherCreateIconFunctions() {
        doTest(
            """
            fun createIcon(content: () -> Unit) {}

            fun SomeFunction() {
                createIcon { }
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip == "Icon preview" },
        )
    }

    fun testGutterIconShownForUserIconCalls() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.toAttrs
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun SmileyIcon(modifier: Modifier = Modifier) {
                createIcon(renderStyle = IconRenderStyle.Stroke(2), attrs = modifier.toAttrs()) {
                    Circle {
                        cx(12)
                        cy(12)
                        r(10)
                    }
                }
            }

            @Composable
            fun SomePage() {
                SmileyIcon() $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
            assertLineMarker = { _, marker ->
                assertThat(marker.lineMarkerTooltip).isEqualTo("Icon: Smiley")
            }
        )
    }

    fun testGutterIconNotShownForUserFunctionsThatAreNotIcons() {
        doTest(
            """
            fun DrawIcon() {
                println("Not an icon")
            }

            fun SomePage() {
                DrawIcon()
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
        )
    }

    fun testGutterIconNotShownForUnreadableUserIconCalls() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun MaybeFilledIcon(filled: Boolean) {
                createIcon(renderStyle = IconRenderStyle.Fill()) {
                    if (filled) Path { d("M4 4h16v16H4z") }
                }
            }

            @Composable
            fun SomePage() {
                MaybeFilledIcon(filled = true)
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
        )
    }

    fun testGutterIconShownForUserIconCallsWithoutIconSuffix() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun Smiley() {
                createIcon(renderStyle = IconRenderStyle.Stroke(2)) {
                    Circle {
                        cx(12)
                        cy(12)
                        r(10)
                    }
                }
            }

            @Composable
            fun SomePage() {
                Smiley() $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
            assertLineMarker = { _, marker ->
                assertThat(marker.lineMarkerTooltip).isEqualTo("Icon: Smiley")
            }
        )
    }

    fun testGutterIconNotShownForOtherCallsWithoutIconSuffix() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.foundation.layout.Box

            @Composable
            fun Header() {
                Box()
            }

            @Composable
            fun SomePage() {
                Header() // The user's own function, but not an icon
                Box() // From a library
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
        )
    }
}
