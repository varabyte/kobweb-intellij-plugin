package com.varabyte.kobweb.intellij.intentions.style

// WIP: commented out because it could not be compiled/run when written. See KobwebLightTestCase.kt for how to enable.
//
// import com.varabyte.kobweb.intellij.testutil.KobwebLightTestCase
//
// // TODO: Extraction opens a wizard dialog (ExtractCssStyleWizard); it needs to be bypassed/mocked for headless tests.
// class ExtractCssStyleIntentionTest : KobwebLightTestCase() {
//     fun testIntentionIsAvailableOnInlineModifierChain() {
//         myFixture.configureByText(
//             "Page.kt",
//             """
//             import com.varabyte.kobweb.compose.ui.*
//             val m = Modifier.<caret>fillMaxWidth().color("red")
//             """.trimIndent()
//         )
//         myFixture.findSingleIntention("Extract CssStyle")
//     }
// }
