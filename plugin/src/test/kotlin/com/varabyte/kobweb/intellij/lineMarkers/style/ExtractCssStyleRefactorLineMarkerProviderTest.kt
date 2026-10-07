package com.varabyte.kobweb.intellij.lineMarkers.style

// WIP: commented out because it could not be compiled/run when written. See KobwebLightTestCase.kt for how to enable.
//
// import com.varabyte.kobweb.intellij.testutil.KobwebLightTestCase
// import com.varabyte.truthish.assertThat
//
// class ExtractCssStyleRefactorLineMarkerProviderTest : KobwebLightTestCase() {
//     fun testGutterIconShownForInlineModifierChain() {
//         myFixture.configureByText(
//             "Page.kt",
//             """
//             import com.varabyte.kobweb.compose.ui.*
//             val m = Modifier.fillMaxWidth().color("red")
//             """.trimIndent()
//         )
//         myFixture.doHighlighting()
//         val markers = com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl.getLineMarkers(
//             myFixture.editor.document, project
//         )
//         assertThat(markers.any { it.lineMarkerTooltip?.contains("Extract") == true }).isTrue()
//     }
// }
