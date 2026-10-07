package com.varabyte.kobweb.intellij.inspections

// WIP: commented out because it could not be compiled/run when written. See KobwebLightTestCase.kt for how to enable.
//
// import com.varabyte.kobweb.intellij.inspections.modifier.DanglingModifierInspection
// import com.varabyte.kobweb.intellij.testutil.KobwebLightTestCase
// import com.varabyte.truthish.assertThat
//
// class DanglingModifierInspectionTest : KobwebLightTestCase() {
//     fun testUnusedModifierChainIsFlagged() {
//         myFixture.enableInspections(DanglingModifierInspection())
//         myFixture.configureByText(
//             "Dangling.kt",
//             """
//             import com.varabyte.kobweb.compose.ui.*
//             fun foo() {
//                 Modifier.fillMaxWidth()
//             }
//             """.trimIndent()
//         )
//         // Also exercises SafeDeleteExpressionQuickFix
//         assertThat(myFixture.doHighlighting().isNotEmpty()).isTrue()
//     }
// }
