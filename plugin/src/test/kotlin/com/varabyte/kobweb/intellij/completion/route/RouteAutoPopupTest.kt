@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.completion.route

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language

class RouteAutoPopupTest : KobwebApplicationTestCase() {
    private lateinit var tester: CompletionAutoPopupTester

    // Auto-popups are scheduled asynchronously, so the tester needs to wait for them away from the UI thread
    override fun runInDispatchThread() = false

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        tester.runWithAutoPopupEnabled(testRunnable)
    }

    override fun setUp() {
        super.setUp()
        tester = CompletionAutoPopupTester(myFixture)

        runInEdtAndWait {
            myFixture.addFileToProject(
                "pages/blog/Post.kt",
                // language=kotlin
                """
                package $TEST_GROUP.pages.blog

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page

                @Page
                @Composable
                fun BlogPostPage() {
                }
                """.trimIndent()
            )
        }
    }

    private fun configure(@Language("kotlin") code: String) {
        runInEdtAndWait { myFixture.configureByText("Nav.kt", code) }
    }

    @Language("kotlin")
    private fun produceNav(link: String) = """
        package $TEST_GROUP.components

        import androidx.compose.runtime.Composable
        import com.varabyte.kobweb.silk.components.navigation.Link

        @Composable
        fun Nav() {
            Link("$link")
        }
    """.trimIndent()

    private val lookupStrings get() = tester.lookup?.items?.map { it.lookupString }

    fun testTypingSlashShowsPopup() {
        configure(produceNav("<caret>"))

        tester.typeWithPauses("/")

        assertThat(lookupStrings!!).containsExactly("/blog/post")
    }

    fun testTypingLetterInRouteShowsPopup() {
        configure(produceNav("/<caret>"))

        tester.typeWithPauses("bl")

        assertThat(lookupStrings!!).containsExactly("/blog/post")
    }

    fun testTypingInStringsNotPassedToRouteParametersDoesNotShowPopup() {
        configure(
            """
            package $TEST_GROUP.components

            fun log() {
                println("<caret>")
            }
            """.trimIndent()
        )

        tester.typeWithPauses("/bl")

        assertThat(lookupStrings).isNull()
    }
}
