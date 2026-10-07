package com.varabyte.kobweb.intellij.inspections

// WIP: commented out because it could not be compiled/run when written. See KobwebLightTestCase.kt for how to enable.
//
// import com.varabyte.kobweb.intellij.inspections.supressors.UnusedInspectionSuppressor
// import com.varabyte.kobweb.intellij.testutil.KobwebLightTestCase
// import com.varabyte.truthish.assertThat
// import org.jetbrains.kotlin.psi.KtNamedFunction
//
// class UnusedInspectionSuppressorTest : KobwebLightTestCase() {
//     fun testPageFunctionsAreSuppressedForUnused() {
//         myFixture.addFileToProject(
//             "stubs/Page.kt",
//             "package com.varabyte.kobweb.core\nannotation class Page"
//         )
//         val file = myFixture.configureByText(
//             "Index.kt",
//             """
//             import com.varabyte.kobweb.core.Page
//             @Page fun Home() {}
//             fun helper() {}
//             """.trimIndent()
//         )
//         val functions = file.children.filterIsInstance<KtNamedFunction>().associateBy { it.name }
//         val suppressor = UnusedInspectionSuppressor()
//         assertThat(suppressor.isSuppressedFor(functions.getValue("Home"), "unused")).isTrue()
//         assertThat(suppressor.isSuppressedFor(functions.getValue("helper"), "unused")).isFalse()
//     }
// }
