package com.varabyte.kobweb.intellij.intentions.style

import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase
import com.varabyte.kobweb.intellij.wizards.style.ExtractCssStyleWizard

// We cannot run the intention (because it would try to bring up a dialog popup), but we can at least assert that it is
// available in expected locations. See ExtractCssStyleWizardResultTest for code that tests the actual extract behavior.
class ExtractCssStyleIntentionTest : KobwebIntentionTestBase() {
    override val intentionName: String = ExtractCssStyleWizard.TITLE

    fun testExtractCssStyleIntentionAvailableAnywhereInModifierChain() {
        assertIntentionIsAvailable(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                Modifi<caret>er.width(50.percent).color(Colors.Blue)
            }
            """.trimIndent(),
        )

        assertIntentionIsAvailable(
            """
            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import org.jetbrains.compose.web.css.percent

            @Composable
            fun SomePage() {
                Modifier.width(50.percent).col<caret>or(Colors.Blue)
            }
            """.trimIndent(),
        )
    }
}