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

    fun testGutterIconShownForRenamedStyles() {
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
        val expectedTooltips = listOf(
            "Font Awesome: square (regular)",
            "Font Awesome: square (solid)",
        )

        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.icons.fa.*

            @Composable
            fun SomePage() {
                FaSquare(style = FaIconStyle.REGULAR) $GUTTER_TAG
                FaSquare(style = FaIconStyle.SOLID) $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { isIconPreview(it.lineMarkerTooltip) },
            assertLineMarker = { index, marker ->
                assertThat(marker.lineMarkerTooltip).isEqualTo(expectedTooltips[index])
            }
        )
    }

    fun testGutterIconNotShownForMdiIconHelper() {
        // An icon named "icon" would match `MdiIcon` if the helper were mistaken for an icon
        IconPreviewService.instance.useIconSetForTests(
            SilkIconSet.MATERIAL_ICONS,
            IconSet(
                "mdi",
                listOf(IconStyle("mdi", listOf(IconShape("icon", 1f, Path2D.Float(Rectangle2D.Float(0f, 0f, 1f, 1f)))))),
            ),
            testRootDisposable,
        )
        // A stand-in for the `silk-icons-mdi` artifact, which the tests don't depend on
        myFixture.addFileToProject(
            "silk/MdiIcons.kt",
            """
            package com.varabyte.kobweb.silk.components.icons.mdi

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier

            enum class MdiIconStyle { FILLED, OUTLINED }

            @Composable
            fun MdiIcon(name: String, modifier: Modifier, style: MdiIconStyle = MdiIconStyle.FILLED) {}
            """.trimIndent()
        )

        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.silk.components.icons.mdi.*

            @Composable
            fun SomePage() {
                MdiIcon("home", Modifier) // Helper function, not an icon
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith(SilkIconSet.MATERIAL_ICONS.displayName) == true },
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

    fun testGutterIconShownForSvgCalls() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*

            @Composable
            fun SomePage() {
                Svg(attrs = { viewBox(0, 0, 24, 24) }) { $GUTTER_TAG
                    Path { d("M4 4h16v16H4z") }
                }
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip == "Icon preview" },
        )
    }

    fun testGutterIconNotShownForUnreadableSvgCalls() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*

            @Composable
            fun SomePage() {
                Svg(attrs = { viewBox(0, 0, 24, 24) }) {
                    Text("Not a shape") { }
                }
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip == "Icon preview" },
        )
    }

    fun testGutterIconShownForUserIconCallsThatUseSvg() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*

            @Composable
            fun Logo() {
                Svg(attrs = { viewBox(0, 0, 24, 24) }) {
                    Path { d("M4 4h16v16H4z") }
                }
            }

            @Composable
            fun SomePage() {
                Logo() $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
            assertLineMarker = { _, marker ->
                assertThat(marker.lineMarkerTooltip).isEqualTo("Icon: Logo")
            }
        )
    }

    fun testGutterIconShownForUserIconCallsThatWrapSvg() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.core.Page
            import org.jetbrains.compose.web.dom.Span

            @Composable
            fun GitHubIcon() {
                // GitHub SVG icon
                Span {
                    Svg(
                        attrs = {
                            viewBox(0, 0, 16, 16)
                            width(20)
                            height(20)
                            fill(SVGFillType.CurrentColor)
                            ariaHidden(true)
                        }
                    ) {
                        Path(
                            attrs = {
                                d(
                                    "M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 " +
                                        "0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13" +
                                        "-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66" +
                                        ".07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15" +
                                        "-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 " +
                                        "1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 " +
                                        "1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 " +
                                        "1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"
                                )
                            }
                        )
                    }
                }
            }

            @Page
            @Composable
            fun HomePage() {
                GitHubIcon() $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
            assertLineMarker = { _, marker ->
                assertThat(marker.lineMarkerTooltip).isEqualTo("Icon: GitHub")
            }
        )
    }

    fun testGutterIconShownForUserFunctionsWithOneSvgAmongOtherContent() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.toAttrs
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.silk.components.navigation.Link
            import com.varabyte.kobweb.silk.components.overlay.PopupPlacement
            import com.varabyte.kobweb.silk.components.overlay.PreviousSibling
            import com.varabyte.kobweb.silk.components.overlay.Tooltip
            import org.jetbrains.compose.web.dom.Span

            @Composable
            fun GitHubSourceLink() {
                Link("https://github.com/example/site") {
                    Span(attrs = Modifier.toAttrs()) {
                        Svg(attrs = { viewBox(0, 0, 16, 16) }) {
                            Path { d("M4 4h8v8H4z") }
                        }
                    }
                }
                Tooltip(PreviousSibling, "View source", placement = PopupPlacement.BottomRight)
            }

            @Page
            @Composable
            fun HomePage() {
                GitHubSourceLink() $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
            assertLineMarker = { _, marker ->
                assertThat(marker.lineMarkerTooltip).isEqualTo("Icon: GitHubSourceLink")
            }
        )
    }

    fun testGutterIconShownForUserIconCallsThatCallAnotherIconAmongOtherContent() {
        val expectedTooltips = listOf(
            "Icon: GitHub",
            "Icon: UnderlinedGitHub",
        )

        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.silk.components.overlay.PreviousSibling
            import com.varabyte.kobweb.silk.components.overlay.Tooltip
            import org.jetbrains.compose.web.dom.Span

            @Composable
            fun GitHubIcon() {
                Svg(attrs = { viewBox(0, 0, 16, 16) }) {
                    Path { d("M4 4h8v8H4z") }
                }
            }

            @Composable
            fun UnderlinedGitHubIcon() {
                Span { GitHubIcon() } $GUTTER_TAG
                Tooltip(PreviousSibling, "GitHub")
            }

            @Page
            @Composable
            fun HomePage() {
                UnderlinedGitHubIcon() $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
            assertLineMarker = { index, marker ->
                assertThat(marker.lineMarkerTooltip).isEqualTo(expectedTooltips[index])
            }
        )
    }

    fun testGutterIconShownForUserFunctionsThatCallAnIconAmongOtherContent() {
        val expectedTooltips = listOf(
            "Icon: GitHub",
            "Icon: GitHubSourceLink",
        )

        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.silk.components.navigation.Link
            import com.varabyte.kobweb.silk.components.overlay.PreviousSibling
            import com.varabyte.kobweb.silk.components.overlay.Tooltip

            @Composable
            fun GitHubIcon() {
                Svg(attrs = { viewBox(0, 0, 16, 16) }) {
                    Path { d("M4 4h8v8H4z") }
                }
            }

            @Composable
            fun GitHubSourceLink() {
                Link("https://github.com/example/site") { GitHubIcon() } $GUTTER_TAG
                Tooltip(PreviousSibling, "View source")
            }

            @Page
            @Composable
            fun HomePage() {
                GitHubSourceLink() $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
            assertLineMarker = { index, marker ->
                assertThat(marker.lineMarkerTooltip).isEqualTo(expectedTooltips[index])
            }
        )
    }

    fun testGutterIconShownForUserFunctionsThatCallASilkIconAmongOtherContent() {
        val expectedTooltips = listOf(
            "Icon: Check",
            "Icon: DoneLabel",
        )

        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.silk.components.icons.CheckIcon
            import org.jetbrains.compose.web.dom.Span
            import org.jetbrains.compose.web.dom.Text

            @Composable
            fun DoneLabel() {
                Span {
                    CheckIcon() $GUTTER_TAG
                    Text("Done")
                }
            }

            @Page
            @Composable
            fun HomePage() {
                DoneLabel() $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
            assertLineMarker = { index, marker ->
                assertThat(marker.lineMarkerTooltip).isEqualTo(expectedTooltips[index])
            }
        )
    }

    fun testGutterIconNotShownForUserFunctionsThatCallMoreThanOneIcon() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.core.Page
            import org.jetbrains.compose.web.dom.Span

            @Composable
            fun GitHubIcon() {
                Svg(attrs = { viewBox(0, 0, 16, 16) }) {
                    Path { d("M4 4h8v8H4z") }
                }
            }

            @Composable
            fun MastodonIcon() {
                Svg(attrs = { viewBox(0, 0, 16, 16) }) {
                    Path { d("M8 8h4v4H8z") }
                }
            }

            @Composable
            fun SocialLinks() {
                Span { GitHubIcon() } $GUTTER_TAG
                Span { MastodonIcon() } $GUTTER_TAG
            }

            @Page
            @Composable
            fun HomePage() {
                SocialLinks() // Draws two icons, so it isn't one
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
        )
    }

    fun testGutterIconNotShownForUserFunctionsThatCallAnIconThroughNonIconFunctions() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.compose.foundation.layout.Column
            import com.varabyte.kobweb.compose.foundation.layout.Row
            import com.varabyte.kobweb.core.Page
            import org.jetbrains.compose.web.dom.Text

            @Composable
            fun Logo() {
                Svg(attrs = { viewBox(0, 0, 16, 16) }) {
                    Path { d("M4 4h8v8H4z") }
                }
            }

            @Composable
            fun NavHeader() {
                Row {
                    Logo() $GUTTER_TAG
                    Text("My Site")
                }
            }

            @Composable
            fun PageLayout(title: String, content: @Composable () -> Unit) {
                Column {
                    NavHeader() // Its logo isn't one of its icons, as `Logo` isn't named like one
                    Text(title)
                    content()
                }
            }

            @Page
            @Composable
            fun HomePage() {
                PageLayout("Home") {
                    Text("Welcome")
                }
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip?.startsWith("Icon:") == true },
            assertLineMarker = { _, marker ->
                assertThat(marker.lineMarkerTooltip).isEqualTo("Icon: Logo")
            }
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

    fun testGutterIconNotShownForUserFunctionsWithMoreThanOneSvg() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*

            @Composable
            fun SocialIcons() {
                Svg(attrs = { viewBox(0, 0, 24, 24) }) {
                    Path { d("M4 4h16v16H4z") }
                }
                Svg(attrs = { viewBox(0, 0, 24, 24) }) {
                    Path { d("M8 8h8v8H8z") }
                }
            }

            @Composable
            fun SomePage() {
                SocialIcons()
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
