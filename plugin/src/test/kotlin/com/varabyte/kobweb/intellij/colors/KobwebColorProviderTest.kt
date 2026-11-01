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
                val colorIcon = info.icon as ColorIcon
                when (index) {
                    0 -> colorIcon.iconColor.rgb == 0x00FF00
                    2 -> colorIcon.iconColor.rgb == 0x0000FF
                }
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
}