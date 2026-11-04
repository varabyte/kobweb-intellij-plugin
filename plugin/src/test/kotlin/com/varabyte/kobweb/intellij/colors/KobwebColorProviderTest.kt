@file:Suppress("LocalVariableName")

package com.varabyte.kobweb.intellij.colors

import com.intellij.util.ui.ColorIcon
import com.varabyte.kobweb.intellij.lineMarkers.KobwebLineMarkerTestBase
import com.varabyte.truthish.assertThat
import java.awt.Color

class KobwebColorProviderTest : KobwebLineMarkerTestBase() {
    fun testColorsAreExposedInTheGutter() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.graphics.Colors.Green $GUTTER_TAG
            import com.varabyte.kobweb.compose.ui.graphics.*

            private val THEME_COLOR = Colors.Blue $GUTTER_TAG
            """.trimIndent(),
            acceptLineMarker = { it.icon is ColorIcon },
            assertLineMarker = { index, info ->
                val expected = when (index) {
                    // Note that CSS's "green" is a darker green than #00FF00 (which CSS calls "lime")
                    0 -> Color(0x008000)
                    else -> Color(0x0000FF)
                }
                assertThat((info.icon as ColorIcon).iconColor).isEqualTo(expected)
            }
        )
    }

    fun testHslColorWithNegativeHue() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.graphics.Color

            // Same as a hue of 270
            private val PURPLE = Color.hsl(-90, 1f, 0.5f) $GUTTER_TAG
            """.trimIndent(),
            acceptLineMarker = { it.icon is ColorIcon },
            assertLineMarker = { _, info ->
                assertThat((info.icon as ColorIcon).iconColor).isEqualTo(Color(0x7F00FF))
            }
        )
    }

    fun testHslColorWithTinyNegativeHue() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.graphics.Color

            // `mod` rounds this up to exactly 360, which is the same as a hue of 0
            private val RED = Color.hsl(-0.00001f, 1f, 0.5f) $GUTTER_TAG
            """.trimIndent(),
            acceptLineMarker = { it.icon is ColorIcon },
            assertLineMarker = { _, info ->
                assertThat((info.icon as ColorIcon).iconColor).isEqualTo(Color(0xFF0000))
            }
        )
    }

    fun testHslColorWithFractionalHue() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.graphics.Color

            private val ALMOST_YELLOW = Color.hsl(59.6f, 1f, 0.5f) $GUTTER_TAG
            private val ALMOST_RED = Color.hsl(359.7f, 1f, 0.5f) $GUTTER_TAG
            """.trimIndent(),
            acceptLineMarker = { it.icon is ColorIcon },
            assertLineMarker = { index, info ->
                val expected = when (index) {
                    0 -> Color(0xFFFD00)
                    else -> Color(0xFF0001)
                }
                assertThat((info.icon as ColorIcon).iconColor).isEqualTo(expected)
            }
        )
    }

    fun testHslColorWithOutOfRangeSaturationAndLightness() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.graphics.Color

            // Same as a saturation of 1 and a lightness of 0
            private val BLACK = Color.hsl(0, 2f, -1f) $GUTTER_TAG
            // Same as a saturation of 1 and a lightness of 1
            private val WHITE = Color.hsl(0, 2f, 2f) $GUTTER_TAG
            // Same as a saturation of 1 (as opposed to clamping each RGB channel after converting)
            private val DARK_RED = Color.hsl(0, 2f, 0.25f) $GUTTER_TAG
            """.trimIndent(),
            acceptLineMarker = { it.icon is ColorIcon },
            assertLineMarker = { index, info ->
                val expected = when (index) {
                    0 -> Color(0x000000)
                    1 -> Color(0xFFFFFF)
                    else -> Color(0x7F0000)
                }
                assertThat((info.icon as ColorIcon).iconColor).isEqualTo(expected)
            }
        )
    }

    fun testRgbColorWithOutOfRangeChannels() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.graphics.Color

            // Same as 255, 0, 0
            private val RED = Color.rgb(300, 0, -20) $GUTTER_TAG
            """.trimIndent(),
            acceptLineMarker = { it.icon is ColorIcon },
            assertLineMarker = { _, info ->
                assertThat((info.icon as ColorIcon).iconColor).isEqualTo(Color(0xFF0000))
            }
        )
    }

    fun testColorWithNamedArgumentsOutOfOrder() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.graphics.Color

            private val BLUE_RGB = Color.rgb(b = 255, g = 0, r = 0) $GUTTER_TAG
            private val BLUE_RGBA = Color.rgba(a = 255, r = 0, g = 0, b = 255) $GUTTER_TAG
            private val BLUE_ARGB = Color.argb(r = 0, g = 0, b = 255, a = 255) $GUTTER_TAG
            private val BLUE_HSL = Color.hsl(l = 0.5f, s = 1f, h = 240) $GUTTER_TAG
            private val BLUE_HSLA = Color.hsla(a = 1f, l = 0.5f, s = 1f, h = 240) $GUTTER_TAG
            """.trimIndent(),
            acceptLineMarker = { it.icon is ColorIcon },
            assertLineMarker = { _, info ->
                assertThat((info.icon as ColorIcon).iconColor).isEqualTo(Color(0x0000FF))
            }
        )
    }
}