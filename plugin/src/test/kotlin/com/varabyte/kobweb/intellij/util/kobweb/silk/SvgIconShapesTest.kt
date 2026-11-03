@file:Suppress("FunctionName", "LocalVariableName")

package com.varabyte.kobweb.intellij.util.kobweb.silk

import com.intellij.psi.search.GlobalSearchScope
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertAll
import com.varabyte.truthish.assertThat
import com.varabyte.truthish.assertThrows
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionByPackageIndex
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class SvgIconShapesTest : KobwebApplicationTestCase() {
    /**
     * Asserts the icon's outline covers the given box, in its view box's coordinates (24 units tall unless a test says
     * otherwise).
     */
    private fun IconShape.assertBounds(x: Double, y: Double, width: Double, height: Double, viewBoxHeight: Int = 24) {
        val bounds = path.bounds2D
        assertAll {
            that(bounds.x * viewBoxHeight).isWithin(0.01).of(x)
            that(bounds.y * viewBoxHeight).isWithin(0.01).of(y)
            that(bounds.width * viewBoxHeight).isWithin(0.01).of(width)
            that(bounds.height * viewBoxHeight).isWithin(0.01).of(height)
        }
    }

    fun testFilledPath() {
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun TestIcon() {
                createIcon(renderStyle = IconRenderStyle.Fill()) {
                    Path { d("M4 4h16v16H4z") }
                }
            }
            """.trimIndent(),
        )
        val icon = (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
            .single { it.name == "TestIcon" }.readSvgIconShape()
        assertThat(icon.name).isEqualTo("TestIcon")
        assertThat(icon.width).isEqualTo(1f)
        icon.assertBounds(4.0, 4.0, 16.0, 16.0)
    }

    fun testPathDataBuilder() {
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun TestIcon() {
                createIcon(renderStyle = IconRenderStyle.Fill()) {
                    Path {
                        d {
                            moveTo(4, 4)
                            horizontalLineTo(16, isRelative = true)
                            verticalLineTo(16, true)
                            lineTo(-16, 0, isRelative = true)
                            closePath()
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        val icon = (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
            .single { it.name == "TestIcon" }.readSvgIconShape()
        icon.assertBounds(4.0, 4.0, 16.0, 16.0)
    }

    fun testPathDataJoinedFromSeveralStrings() {
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun TestIcon() {
                createIcon(renderStyle = IconRenderStyle.Fill()) {
                    Path {
                        d(
                            "M4 4h16" +
                                "v16H4z"
                        )
                    }
                }
            }
            """.trimIndent(),
        )
        val icon = (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
            .single { it.name == "TestIcon" }.readSvgIconShape()
        icon.assertBounds(4.0, 4.0, 16.0, 16.0)
    }

    fun testStrokesBecomeOutlines() {
        // With SVG's default butt caps, the stroke ends where the line does
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun TestIcon() {
                createIcon(renderStyle = IconRenderStyle.Stroke(4)) {
                    Line {
                        x1(3)
                        x2(21)
                        y1(12)
                        y2(12)
                    }
                }
            }
            """.trimIndent(),
        )
        val icon = (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
            .single { it.name == "TestIcon" }.readSvgIconShape()
        icon.assertBounds(3.0, 10.0, 18.0, 4.0)
    }

    fun testGroupsPassPresentationAttributesToTheirElements() {
        // Round caps stick out past the line's ends by half the stroke width
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun TestIcon() {
                createIcon(renderStyle = IconRenderStyle.Stroke(4)) {
                    Group(attrs = { strokeLineCap(SVGStrokeLineCap.Round) }) {
                        Line {
                            x1(3)
                            x2(21)
                            y1(12)
                            y2(12)
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        val icon = (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
            .single { it.name == "TestIcon" }.readSvgIconShape()
        icon.assertBounds(1.0, 10.0, 22.0, 4.0)
    }

    fun testViewBox() {
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun TestIcon() {
                createIcon(ViewBox.sized(24, 20), renderStyle = IconRenderStyle.Fill()) {
                    Path { d("M0 0h24v20H0z") }
                }
            }
            """.trimIndent(),
        )
        val wideIcon = (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
            .single { it.name == "TestIcon" }.readSvgIconShape()
        assertThat(wideIcon.width).isEqualTo(1.2f)
        wideIcon.assertBounds(0.0, 0.0, 24.0, 20.0, viewBoxHeight = 20)

        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun TestIcon() {
                createIcon(viewBox = ViewBox(4, 4, 16, 16), renderStyle = IconRenderStyle.Fill()) {
                    Path { d("M8 8h8v8H8z") }
                }
            }
            """.trimIndent(),
        )
        val offsetIcon = (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
            .single { it.name == "TestIcon" }.readSvgIconShape()
        offsetIcon.assertBounds(4.0, 4.0, 8.0, 8.0, viewBoxHeight = 16)
    }

    fun testShapes() {
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun CircleTestIcon() {
                createIcon(renderStyle = IconRenderStyle.Fill()) {
                    Circle {
                        cx(12)
                        cy(12)
                        r(8)
                    }
                }
            }

            @Composable
            fun RectTestIcon() {
                createIcon(renderStyle = IconRenderStyle.Fill()) {
                    Rect {
                        x(2)
                        y(6)
                        width(20)
                        height(12)
                        rx(2)
                    }
                }
            }

            @Composable
            fun PolylineTestIcon() {
                createIcon(renderStyle = IconRenderStyle.Fill()) {
                    Polyline {
                        points(3 to 12, 9 to 18, 21 to 2)
                    }
                }
            }
            """.trimIndent(),
        )
        val functions = (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
        functions.single { it.name == "CircleTestIcon" }.readSvgIconShape().assertBounds(4.0, 4.0, 16.0, 16.0)
        functions.single { it.name == "RectTestIcon" }.readSvgIconShape().assertBounds(2.0, 6.0, 20.0, 12.0)
        functions.single { it.name == "PolylineTestIcon" }.readSvgIconShape().assertBounds(3.0, 2.0, 18.0, 16.0)
    }

    fun testLoopOverLiteralValues() {
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun TestIcon() {
                createIcon(renderStyle = IconRenderStyle.Stroke(2)) {
                    for (y in listOf(4, 12, 20)) {
                        Line {
                            x1(0)
                            x2(24)
                            y1(y)
                            y2(y)
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        val icon = (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
            .single { it.name == "TestIcon" }.readSvgIconShape()
        icon.assertBounds(0.0, 3.0, 24.0, 18.0)
    }

    fun testIconThatCallsAnotherIcon() {
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.toAttrs
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun TestIcon(modifier: Modifier = Modifier) {
                SquareTestIcon(modifier)
            }

            @Composable
            fun SquareTestIcon(modifier: Modifier = Modifier) {
                createIcon(renderStyle = IconRenderStyle.Fill(), attrs = modifier.toAttrs()) {
                    Path { d("M4 4h16v16H4z") }
                }
            }
            """.trimIndent(),
        )
        val icon = (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
            .single { it.name == "TestIcon" }.readSvgIconShape()
        assertThat(icon.name).isEqualTo("TestIcon")
        icon.assertBounds(4.0, 4.0, 16.0, 16.0)
    }

    fun testUnsupportedCodeFailsTheWholeIcon() {
        // An `if`, even alongside elements that could be read
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun TestIcon(filled: Boolean) {
                createIcon {
                    Path { d("M4 4h16v16H4z") }
                    if (filled) Path { d("M8 8h8v8H8z") }
                }
            }
            """.trimIndent(),
        )
        assertThrows<IllegalArgumentException> {
            (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
                .single { it.name == "TestIcon" }.readSvgIconShape()
        }

        // A value from elsewhere
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            private const val SQUARE = "M4 4h16v16H4z"

            @Composable
            fun TestIcon() {
                createIcon {
                    Path { d(SQUARE) }
                }
            }
            """.trimIndent(),
        )
        assertThrows<IllegalArgumentException> {
            (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
                .single { it.name == "TestIcon" }.readSvgIconShape()
        }

        // Attributes on the icon itself, which could change its shape
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun TestIcon() {
                createIcon(attrs = { strokeWidth(8) }) {
                    Path { d("M4 4h16v16H4z") }
                }
            }
            """.trimIndent(),
        )
        assertThrows<IllegalArgumentException> {
            (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
                .single { it.name == "TestIcon" }.readSvgIconShape()
        }

        // An attribute this reader doesn't know
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.dom.svg.*
            import com.varabyte.kobweb.silk.components.icons.*

            @Composable
            fun TestIcon() {
                createIcon {
                    Path {
                        transform { rotate(45) }
                        d("M4 4h16v16H4z")
                    }
                }
            }
            """.trimIndent(),
        )
        assertThrows<IllegalArgumentException> {
            (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
                .single { it.name == "TestIcon" }.readSvgIconShape()
        }

        // A body that does something other than call `createIcon`
        myFixture.configureByText(
            "TestIcons.kt",
            // language=kotlin
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun TestIcon() {
                println("Not an icon")
            }
            """.trimIndent(),
        )
        assertThrows<IllegalArgumentException> {
            (myFixture.file as KtFile).declarations.filterIsInstance<KtNamedFunction>()
                .single { it.name == "TestIcon" }.readSvgIconShape()
        }
    }

    fun testAllKobwebIconsCanBeRead() {
        // Every icon Kobweb defines in code, read from the sources of the Kobweb version these tests run against
        val icons = KotlinTopLevelFunctionByPackageIndex[
            "com.varabyte.kobweb.silk.components.icons", project, GlobalSearchScope.allScope(project)
        ]
            .filter { it.name.orEmpty().endsWith("Icon") && it.name != "createIcon" }
            .map { it.navigationElement as KtNamedFunction }
            .distinctBy { it.name }

        // Kobweb 0.25.1 has 40 icons. More is fine (e.g. after upgrading Kobweb), but fewer suggests a lookup problem
        assertThat(icons.size).isGreaterThanEqual(40)
        assertAll {
            for (icon in icons) {
                withMessage("${icon.name} couldn't be read")
                    .that(runCatching { icon.readSvgIconShape() }.exceptionOrNull()?.message)
                    .isNull()
            }
        }
    }
}
