package com.varabyte.kobweb.intellij.lineMarkers.style

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.kobweb.intellij.wizards.cssstyle.ExtractCssStyleWizard
import com.varabyte.truthish.assertAll
import com.varabyte.truthish.assertWithMessage

private const val GUTTER_COMMENT = "// Gutter"

class ExtractCssStyleRefactorLineMarkerProviderTest : KobwebApplicationTestCase() {
    fun testGutterIconShownForInlineModifierChain() {
        // Gutter icons only show up for modifiers inside @Composable contexts (excluding CssStyle extraModifier blocks.
        val code =
            // language=kotlin
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.graphics.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.*
            import org.jetbrains.compose.web.css.*

            val NO_GUTTER_MODIFIER_PROPERTY = Modifier.fillMaxWidth().color(Colors.Green)

            val SomeStyle = CssStyle(extraModifier = { Modifier.id("hi") }) { // No gutter
                base {
                    Modifier.fillMaxWidth().color(Colors.Green) // No gutter
                }
            }

            @Composable
            fun SomePage() {
                Modifier.fillMaxWidth().color(Colors.Green) $GUTTER_COMMENT
                val id = "id"
                Modifier.id(id).borderRadius(5.px) $GUTTER_COMMENT
            }
            """.trimIndent()

        val gutterLines = code.lines().mapIndexedNotNull { index, line -> index.takeIf { line.endsWith(GUTTER_COMMENT) } }

        myFixture.configureByText("SomePage.kt", code)
        myFixture.doHighlighting()
        @Suppress("JetBrainsInternalApiUsage") // It's just a test, should be OK?
        val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
            .filter { it.lineMarkerTooltip == ExtractCssStyleWizard.TITLE }

        assertWithMessage("Expected number of gutters found").that(gutterLines.size).isEqualTo(markers.size)

        assertAll {
            markers.forEachIndexed { index, marker ->
                val line = myFixture.editor.offsetToVisualLine(marker.startOffset, true)
                withMessage("Line $line has a marker").that(line).isEqualTo(gutterLines[index])
            }
        }
    }
}
