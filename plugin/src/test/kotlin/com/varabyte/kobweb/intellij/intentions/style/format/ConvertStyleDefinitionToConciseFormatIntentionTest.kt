package com.varabyte.kobweb.intellij.intentions.style.format

// WIP: commented out because it could not be compiled/run when written. See KobwebLightTestCase.kt for how to enable.
//
// import com.varabyte.kobweb.intellij.testutil.KobwebLightTestCase
//
// // Covers the family of format intentions (ConvertStyleDefinitionTo{Concise,Relaxed}FormatIntention, etc.)
// class ConvertStyleDefinitionToConciseFormatIntentionTest : KobwebLightTestCase() {
//     fun testRelaxedToConcise() {
//         myFixture.configureByText(
//             "Styles.kt",
//             """
//             import com.varabyte.kobweb.silk.style.*
//             val MyStyle = Css<caret>Style { base { Modifier.fillMaxWidth() } }
//             """.trimIndent()
//         )
//         val intention = myFixture.findSingleIntention("Convert to concise CssStyle format")
//         myFixture.launchAction(intention)
//         // TODO: assert on myFixture.checkResult(...) once the expected generated code (including imports) is confirmed
//     }
// }
