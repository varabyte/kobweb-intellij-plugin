package com.varabyte.kobweb.intellij.annotations

// WIP: commented out because it could not be compiled/run when written. See KobwebLightTestCase.kt for how to enable.
//
// import com.intellij.lang.annotation.HighlightSeverity
// import com.varabyte.kobweb.intellij.testutil.KobwebLightTestCase
// import com.varabyte.truthish.assertThat
//
// // TODO: Add a stub attribute modifier extension (e.g. `Modifier.id(...)` in the `com.varabyte.kobweb.compose.attributes`
// //  package) to KobwebLightTestCase so the annotator can recognize it; see WebModifierType / isModifierChainingExtension.
// class AttrModifierFunctionAnnotatorTest : KobwebLightTestCase() {
//     fun testAttrModifierIsAnnotated() {
//         myFixture.configureByText("A.kt", "import com.varabyte.kobweb.compose.ui.*\nval m = Modifier.id(\"x\")")
//         val infos = myFixture.doHighlighting(HighlightSeverity.INFORMATION)
//         assertThat(infos.any { it.forcedTextAttributesKey?.externalName == "ATTR_MODIFIER" }).isTrue()
//     }
// }
