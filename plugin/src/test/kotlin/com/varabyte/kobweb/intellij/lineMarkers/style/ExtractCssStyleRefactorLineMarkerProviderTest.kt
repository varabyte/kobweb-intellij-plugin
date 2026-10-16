package com.varabyte.kobweb.intellij.lineMarkers.style

import com.varabyte.kobweb.intellij.lineMarkers.KobwebLineMarkerTestBase
import com.varabyte.kobweb.intellij.wizards.style.ExtractCssStyleWizard

class ExtractCssStyleRefactorLineMarkerProviderTest : KobwebLineMarkerTestBase() {
    fun testGutterIconShownForInlineModifierChain() {
        doTest(
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
                Modifier.fillMaxWidth().color(Colors.Green) $GUTTER_TAG
                val id = "id"
                Modifier.id(id).borderRadius(5.px) $GUTTER_TAG
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip == ExtractCssStyleWizard.TITLE }
        )
    }

    fun testGutterIconExcludedFromNonComposableBlocks() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.graphics.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.*
            import org.jetbrains.compose.web.css.*

            fun nonComposableBlock(block: () -> Modifier) {
                block()
            }

            @Composable
            fun SomePage() {
                Modifier.color(Colors.Green) $GUTTER_TAG
                nonComposableBlock { Modifier.color(Colors.Red) }
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip == ExtractCssStyleWizard.TITLE }
        )
    }

    fun testInlineLambdasAllowGutterIconsToShowUp() {
        doTest(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.graphics.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.*
            import org.jetbrains.compose.web.css.*

            @Composable
            fun SomePage() {
                Modifier.color(Colors.Green) $GUTTER_TAG
                run {
                    with(Unit) {
                        Modifier.color(Colors.Green) $GUTTER_TAG
                    }
                }
            }
            """.trimIndent(),
            acceptLineMarker = { it.lineMarkerTooltip == ExtractCssStyleWizard.TITLE }
        )
    }
}
