package com.varabyte.kobweb.intellij.completion.layout

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language

class LayoutPathAutoPopupTest : KobwebApplicationTestCase() {
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

    private fun addLayout(name: String) {
        @Language("kotlin")
        val code = """
            package $TEST_GROUP.components.layouts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.layout.Layout

            @Layout
            @Composable
            fun $name(content: @Composable () -> Unit) {
                content()
            }
        """.trimIndent()

        runInEdtAndWait { myFixture.addFileToProject("$name.kt", code) }
    }

    private fun configure(@Language("kotlin") code: String) {
        runInEdtAndWait { myFixture.configureByText("Index.kt", code) }
    }

    @Language("kotlin")
    private fun producePage(layoutPath: String) = """
        package $TEST_GROUP.pages

        import com.varabyte.kobweb.core.Page
        import com.varabyte.kobweb.core.layout.Layout

        @Page
        @Layout("$layoutPath")
        fun Index() {
        }
    """.trimIndent()

    private val lookupStrings get() = tester.lookup?.items?.map { it.lookupString }

    fun testTypingLetterInLayoutPathShowsPopup() {
        addLayout("BlogLayout")
        addLayout("BlogPostLayout")
        configure(producePage("<caret>"))

        tester.typeWithPauses("Blog")

        assertThat(lookupStrings!!).containsExactly(
            ".components.layouts.BlogLayout",
            ".components.layouts.BlogPostLayout",
        )
    }

    fun testTypingRelativePathShowsPopup() {
        addLayout("BlogLayout")
        addLayout("BlogPostLayout")
        configure(producePage("<caret>"))

        tester.typeWithPauses(".co")

        assertThat(lookupStrings!!).containsExactly(
            ".components.layouts.BlogLayout",
            ".components.layouts.BlogPostLayout",
        )
    }

    fun testTypingInOtherStringsDoesNotShowPopup() {
        addLayout("BlogLayout")
        configure(
            """
            package $TEST_GROUP.pages

            val text = "<caret>"
            """.trimIndent()
        )

        tester.typeWithPauses("Blog")

        assertThat(lookupStrings).isNull()
    }
}
