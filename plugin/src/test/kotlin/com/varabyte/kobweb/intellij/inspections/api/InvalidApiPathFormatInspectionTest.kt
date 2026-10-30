@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.api

import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

class InvalidApiPathFormatInspectionTest : KobwebFullStackTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(InvalidApiPathFormatInspection())
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun checkHighlighting(@Language("kotlin") code: String) {
        configureByText(Platform.Js, "Client.kt", code)
        allowAnalysisOnEdt { myFixture.checkHighlighting() }
    }

    fun testFullUrlsAreFlaggedInApiPaths() {
        checkHighlighting(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.browser.api
            import kotlinx.browser.window

            suspend fun fetch() {
                window.api.get("hello")
                window.api.get("users?id=5")
                window.api.get("<error descr="Only a path relative to \"/api/\", such as \"auth/login\", is expected here, not a full URL.">https://example.com/api/hello</error>")
                window.api.get("<error>//example.com/api/hello</error>")
            }
            """.trimIndent()
        )
    }

    fun testFullUrlsAndQueriesAreFlaggedInStreamPaths() {
        checkHighlighting(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.streams.ApiStream

            fun connect() {
                ApiStream("chat")
                ApiStream("<error descr="Only the path of an API stream, such as \"chat\", is expected here, not a full URL.">wss://example.com/chat</error>")
                ApiStream("chat<error descr="Only the path of an API stream, such as \"chat\", is expected here, without query parameters or a fragment.">?room=general</error>")
                ApiStream("chat<error>#general</error>")
            }
            """.trimIndent()
        )
    }

    fun testOtherStringsAreNotFlagged() {
        checkHighlighting(
            """
            package $TEST_GROUP.components

            fun log() {
                println("https://example.com/api/hello")
                println("chat?room=general")
            }
            """.trimIndent()
        )
    }
}
