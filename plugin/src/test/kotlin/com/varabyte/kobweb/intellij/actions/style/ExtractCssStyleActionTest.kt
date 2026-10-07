package com.varabyte.kobweb.intellij.actions.style

// WIP: commented out because it could not be compiled/run when written. See KobwebLightTestCase.kt for how to enable.
//
// import com.intellij.openapi.actionSystem.ActionManager
// import com.intellij.testFramework.TestActionEvent
// import com.varabyte.kobweb.intellij.testutil.KobwebLightTestCase
// import com.varabyte.truthish.assertThat
//
// class ExtractCssStyleActionTest : KobwebLightTestCase() {
//     fun testActionVisibleOnlyOnModifierChain() {
//         val action = ActionManager.getInstance().getAction("Kobweb.ExtractCssStyle")
//
//         myFixture.configureByText("A.kt", "import com.varabyte.kobweb.compose.ui.*\nval m = Modifier.<caret>fillMaxWidth()")
//         val onChain = TestActionEvent.createTestEvent(action) { (myFixture.editor.component as? javax.swing.JComponent)?.let { _ -> null } }
//         // TODO: build a DataContext containing CommonDataKeys.EDITOR and PSI_FILE (see `myFixture.projectDisposable`
//         //  helpers / SimpleDataContext) and assert `isEnabledAndVisible` is true here and false on a non-chain element.
//         action.update(onChain)
//         assertThat(onChain.presentation.isEnabledAndVisible).isFalse() // placeholder until the data context is wired
//     }
// }
