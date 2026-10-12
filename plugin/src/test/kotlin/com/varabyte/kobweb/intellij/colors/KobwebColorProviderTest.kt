package com.varabyte.kobweb.intellij.colors

import com.intellij.util.ui.ColorIcon
import com.varabyte.kobweb.intellij.lineMarkers.KobwebLineMarkerTestBase

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
}