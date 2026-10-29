@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.references.layout

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.psi.KtNamedFunction

class LayoutPathGotoDeclarationHandlerTest : KobwebApplicationTestCase() {
    override fun setUp() {
        super.setUp()

        myFixture.addFileToProject(
            "PageLayout.kt",
            // language=kotlin
            """
            package $TEST_GROUP.components.layouts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.layout.Layout

            @Layout
            @Composable
            fun PageLayout(content: @Composable () -> Unit) {
                content()
            }
            """.trimIndent()
        )
    }

    @Language("kotlin")
    private fun producePageUsingLayout(layoutPath: String) = """
        package $TEST_GROUP.pages

        import androidx.compose.runtime.Composable
        import com.varabyte.kobweb.core.Page
        import com.varabyte.kobweb.core.layout.Layout

        @Page
        @Layout("$layoutPath")
        @Composable
        fun HomePage() {
        }
    """.trimIndent()

    /**
     * Returns the names of the functions that Ctrl/Cmd+clicking at the caret in [code] navigates to.
     */
    private fun navigationTargetsAtCaret(@Language("kotlin") code: String): List<String?> {
        myFixture.configureByText("Index.kt", code)
        return GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)
            .map { target -> (target as? KtNamedFunction)?.name }
    }

    fun testPackagePartOfPathLeadsToLayout() {
        assertThat(navigationTargetsAtCaret(producePageUsingLayout(".compo<caret>nents.layouts.PageLayout")))
            .containsExactly("PageLayout")
    }

    fun testStartOfPathLeadsToLayout() {
        assertThat(navigationTargetsAtCaret(producePageUsingLayout("<caret>.components.layouts.PageLayout")))
            .containsExactly("PageLayout")
    }

    fun testLayoutNameStillLeadsToLayout() {
        assertThat(navigationTargetsAtCaret(producePageUsingLayout(".components.layouts.Page<caret>Layout")))
            .containsExactly("PageLayout")
    }

    fun testFullyQualifiedPathLeadsToLayout() {
        assertThat(navigationTargetsAtCaret(producePageUsingLayout("$TEST_GROUP.compo<caret>nents.layouts.PageLayout")))
            .containsExactly("PageLayout")
    }

    fun testInvalidPathLeadsNowhere() {
        assertThat(navigationTargetsAtCaret(producePageUsingLayout(".compo<caret>nents.layouts.MissingLayout")))
            .isEmpty()
    }

    fun testOtherStringsAreUnaffected() {
        assertThat(
            navigationTargetsAtCaret(
                """
                package $TEST_GROUP.components

                fun log() {
                    println(".compo<caret>nents.layouts.PageLayout")
                }
                """.trimIndent()
            )
        ).isEmpty()
    }
}
