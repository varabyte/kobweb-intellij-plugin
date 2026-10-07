package com.varabyte.kobweb.intellij.inspections.style

// WIP: commented out because it could not be compiled/run when written. See KobwebLightTestCase.kt for how to enable.
//
// import com.varabyte.kobweb.intellij.inspections.style.TopLevelStyleSingletonsInspection
// import com.varabyte.kobweb.intellij.testutil.KobwebLightTestCase
// import com.varabyte.truthish.assertThat
//
// class TopLevelStyleSingletonsInspectionTest : KobwebLightTestCase() {
//     fun testStyleInsideFunctionIsFlagged() {
//         myFixture.enableInspections(TopLevelStyleSingletonsInspection())
//         myFixture.configureByText(
//             "Styles.kt",
//             """
//             import com.varabyte.kobweb.silk.style.CssStyle
//             fun foo() {
//                 val MyStyle = CssStyle { }
//             }
//             """.trimIndent()
//         )
//         assertThat(myFixture.doHighlighting().any { it.description?.contains("top level") == true }).isTrue()
//     }
// }
