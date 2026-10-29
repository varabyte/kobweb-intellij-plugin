@file:Suppress("FunctionName", "RedundantSuspendModifier")

package com.varabyte.kobweb.intellij.completion.api

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language

class ApiPathAutoPopupTest : KobwebFullStackTestCase() {
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
            addFileToProject(
                Platform.Jvm,
                "api/Hello.kt",
                // language=kotlin
                """
                package $TEST_GROUP.api

                import com.varabyte.kobweb.api.Api
                import com.varabyte.kobweb.api.ApiContext

                @Api
                suspend fun hello(ctx: ApiContext) {
                }
                """.trimIndent()
            )

            addFileToProject(
                Platform.Jvm,
                "api/Chat.kt",
                // language=kotlin
                """
                package $TEST_GROUP.api

                import com.varabyte.kobweb.api.stream.ApiStream

                val chat = ApiStream { ctx -> }
                """.trimIndent()
            )
        }
    }

    private fun configure(@Language("kotlin") code: String) {
        runInEdtAndWait { configureByText(Platform.Js, "Fetch.kt", code) }
    }

    private val lookupStrings get() = tester.lookup?.items?.map { it.lookupString }

    fun testTypingInApiPathShowsPopup() {
        configure(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.browser.api
            import kotlinx.browser.window

            suspend fun fetch() {
                window.api.get("<caret>")
            }
            """.trimIndent()
        )

        tester.typeWithPauses("he")

        assertThat(lookupStrings!!).containsExactly("hello")
    }

    fun testTypingInApiStreamPathShowsPopup() {
        configure(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.streams.ApiStream

            val stream = ApiStream("<caret>")
            """.trimIndent()
        )

        tester.typeWithPauses("ch")

        assertThat(lookupStrings!!).containsExactly("chat")
    }

    fun testTypingInParameterAnnotatedWithApiPathShowsPopup() {
        runInEdtAndWait {
            // `@ApiPath` is newer than the version of Kobweb these tests use
            addFileToProject(
                Platform.Js,
                "com/varabyte/kobweb/api/ApiPath.kt",
                // language=kotlin
                """
                package com.varabyte.kobweb.api

                annotation class ApiPath(val mustExist: Boolean = true)
                """.trimIndent()
            )
        }
        configure(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.api.ApiPath

            fun fetchLater(@ApiPath apiPath: String) {}

            fun load() {
                fetchLater("<caret>")
            }
            """.trimIndent()
        )

        tester.typeWithPauses("he")

        assertThat(lookupStrings!!).containsExactly("hello")
    }

    fun testTypingInStringsPassedToSameNamedFunctionsDoesNotShowPopup() {
        // Named like `ApiFetcher.get`, but doesn't take an API path
        configure(
            """
            package $TEST_GROUP.components

            fun lookUp(map: Map<String, Int>) {
                map.get("<caret>")
            }
            """.trimIndent()
        )

        tester.typeWithPauses("he")

        assertThat(lookupStrings).isNull()
    }

    fun testTypingInOtherStringsDoesNotShowPopup() {
        configure(
            """
            package $TEST_GROUP.components

            fun log() {
                println("<caret>")
            }
            """.trimIndent()
        )

        tester.typeWithPauses("he")

        assertThat(lookupStrings).isNull()
    }
}
