@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.completion.route

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language

class RouteParamAutoPopupTest : KobwebApplicationTestCase() {
    private lateinit var tester: CompletionAutoPopupTester

    // Auto-popups are scheduled asynchronously, so the tester needs to wait for them away from the UI thread
    override fun runInDispatchThread() = false

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        tester.runWithAutoPopupEnabled(testRunnable)
    }

    override fun setUp() {
        super.setUp()
        tester = CompletionAutoPopupTester(myFixture)
    }

    private fun configure(fileName: String, @Language("kotlin") code: String) {
        runInEdtAndWait { myFixture.configureByText(fileName, code) }
    }

    private val lookupStrings get() = tester.lookup?.items?.map { it.lookupString }

    fun testTypingInRouteParamKeyShowsPopup() {
        configure(
            "Post.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage(ctx: PageContext) {
                ctx.route.params["<caret>"]
            }
            """.trimIndent()
        )

        tester.typeWithPauses("u")

        assertThat(lookupStrings!!).containsExactly("user")
    }

    fun testTypingInKeyOfOtherMapDoesNotShowPopup() {
        configure(
            "Post.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage() {
                val settings = mapOf("user" to "me")
                settings["<caret>"]
            }
            """.trimIndent()
        )

        tester.typeWithPauses("u")

        assertThat(lookupStrings).isNull()
    }
}
