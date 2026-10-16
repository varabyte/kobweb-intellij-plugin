package com.varabyte.kobweb.intellij.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.kobweb.intellij.test.util.findLineMarkers
import com.varabyte.truthish.assertAll
import com.varabyte.truthish.assertWithMessage
import org.intellij.lang.annotations.Language

abstract class KobwebLineMarkerTestBase : KobwebApplicationTestCase() {
    /**
     * A tag that callers should add to the end of the line that they expect a gutter icon to be surfaced on.
     */
    protected val GUTTER_TAG = "// Gutter"

    protected fun doTest(
        @Language("kotlin") code: String,
        acceptLineMarker: (LineMarkerInfo<*>) -> Boolean,
        assertLineMarker: (Int, LineMarkerInfo<*>) -> Unit = { _, _ -> }
    ) {
        myFixture.configureByText("LineMarkerTest.kt", code)
        myFixture.doHighlighting()

        val gutterLines = code.lines().mapIndexedNotNull { index, line -> index.takeIf { line.endsWith(GUTTER_TAG) } }

        val markers = myFixture.findLineMarkers(acceptLineMarker)

        assertWithMessage("Expected number of line markers were not found").that(markers.size).isEqualTo(gutterLines.size)

        assertAll {
            markers.forEachIndexed { index, marker ->
                val line = myFixture.editor.offsetToVisualLine(marker.startOffset, true)
                withMessage("Line marker is not on $line as expected").that(line).isEqualTo(gutterLines[index])
                assertLineMarker(index, marker)
            }
        }
    }
}
