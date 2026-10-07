package com.varabyte.kobweb.intellij.inspections.style

// WIP: commented out because it could not be compiled/run when written. See KobwebLightTestCase.kt for how to enable.
//
// import com.varabyte.kobweb.intellij.inspections.style.PrivateStyleSingletonsInspection
// import com.varabyte.kobweb.intellij.quickfixes.MakePublicQuickFix
// import com.varabyte.kobweb.intellij.testutil.KobwebLightTestCase
// import com.varabyte.truthish.assertThat
//
// class PrivateStyleSingletonsInspectionTest : KobwebLightTestCase() {
//     fun testPrivateStyleIsFlaggedAndCanBeMadePublic() {
//         myFixture.enableInspections(PrivateStyleSingletonsInspection())
//         myFixture.configureByText(
//             "Styles.kt",
//             """
//             import com.varabyte.kobweb.silk.style.CssStyle
//             private val MyStyle = CssStyle { }
//             """.trimIndent()
//         )
//
//         val highlights = myFixture.doHighlighting()
//         assertThat(highlights.any { it.description?.contains("should be public") == true }).isTrue()
//
//         val fix = myFixture.getAllQuickFixes().first { it.text == "Make `MyStyle` public" }
//         myFixture.launchAction(fix)
//         myFixture.checkResult(
//             """
//             import com.varabyte.kobweb.silk.style.CssStyle
//             val MyStyle = CssStyle { }
//             """.trimIndent()
//         )
//     }
//
//     fun testUnderscorePrefixedStyleIsNotFlagged() {
//         myFixture.enableInspections(PrivateStyleSingletonsInspection())
//         myFixture.configureByText(
//             "Styles.kt",
//             """
//             import com.varabyte.kobweb.silk.style.CssStyle
//             private val _MyStyle = CssStyle { }
//             """.trimIndent()
//         )
//         assertThat(myFixture.doHighlighting().none { it.description?.contains("should be public") == true }).isTrue()
//     }
// }
