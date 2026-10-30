@file:Suppress("FunctionName", "RedundantSuspendModifier")

package com.varabyte.kobweb.intellij.inspections.api

import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

class UnknownApiPathInspectionTest : KobwebFullStackTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(UnknownApiPathInspection())

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
            "api/users/User.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api("{}")
            suspend fun user(ctx: ApiContext) {
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

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun checkHighlighting(@Language("kotlin") code: String) {
        configureByText(Platform.Js, "Client.kt", code)
        allowAnalysisOnEdt { myFixture.checkHighlighting() }
    }

    fun testApiPathsThatLeadNowhereAreFlagged() {
        checkHighlighting(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.browser.api
            import kotlinx.browser.window

            suspend fun fetch() {
                window.api.get("hello")
                window.api.get("/hello")
                window.api.post("hello?name=bob")
                window.api.get("users/bob")
                window.api.get("<warning descr="This path doesn't correspond to any known API endpoint.">helo</warning>")
                window.api.get("<warning>helo</warning>?name=bob")
                window.api.get("<warning>users</warning>")
            }
            """.trimIndent()
        )
    }

    fun testApiPathsWithUnknownSegmentsAreOnlyFlaggedIfNothingCouldMatch() {
        checkHighlighting(
            $$"""
            package $$TEST_GROUP.components

            import com.varabyte.kobweb.browser.api
            import kotlinx.browser.window

            suspend fun fetch(id: String, path: String) {
                window.api.get("users/$id")
                window.api.get(path)
                window.api.get("$path/bob")
                window.api.get("<warning>usrs/$id</warning>")
            }
            """.trimIndent()
        )
    }

    fun testApiPathsThatIncludeTheApiPrefixAreCalledOut() {
        checkHighlighting(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.browser.api
            import kotlinx.browser.window

            suspend fun fetch() {
                window.api.get("<warning descr="API paths are already prefixed with \"/api/\", so it shouldn't be included here.">api/hello</warning>")
                window.api.get("<warning descr="API paths are already prefixed with \"/api/\", so it shouldn't be included here.">/api/users/bob</warning>")
            }
            """.trimIndent()
        )
    }

    fun testStreamPathsThatLeadNowhereAreFlagged() {
        checkHighlighting(
            $$"""
            package $$TEST_GROUP.components

            import com.varabyte.kobweb.streams.ApiStream

            fun connect(room: String) {
                ApiStream("chat")
                ApiStream("/chat")
                ApiStream("<warning descr="This path doesn't correspond to any known API stream.">chta</warning>")
                // API endpoints aren't streams
                ApiStream("<warning>hello</warning>")
                // Not known until runtime
                ApiStream("chat-$room")
            }
            """.trimIndent()
        )
    }

    fun testStreamsArentApiEndpoints() {
        checkHighlighting(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.browser.api
            import kotlinx.browser.window

            suspend fun fetch() {
                window.api.get("<warning>chat</warning>")
            }
            """.trimIndent()
        )
    }

    fun testPathsThatDontNeedToExistAreNotFlagged() {
        // Stands in for Kobweb's own annotation, which the version of Kobweb these tests use doesn't have yet
        addFileToProject(
            Platform.Js,
            "ApiPath.kt",
            // language=kotlin
            """
            package com.varabyte.kobweb.api

            @Target(AnnotationTarget.VALUE_PARAMETER)
            annotation class ApiPath(val mustExist: Boolean = true)
            """.trimIndent()
        )

        checkHighlighting(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.api.ApiPath

            fun prefetch(@ApiPath(mustExist = false) apiPath: String) {}
            fun fetchNow(@ApiPath apiPath: String) {}

            fun fetchAll() {
                prefetch("goodbye")
                fetchNow("<warning>goodbye</warning>")
            }
            """.trimIndent()
        )
    }

    fun testFullUrlsAreLeftToTheFormatInspection() {
        checkHighlighting(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.browser.api
            import com.varabyte.kobweb.streams.ApiStream
            import kotlinx.browser.window

            suspend fun fetch() {
                window.api.get("https://example.com/api/hello")
                ApiStream("wss://example.com/chat")
            }
            """.trimIndent()
        )
    }
}
