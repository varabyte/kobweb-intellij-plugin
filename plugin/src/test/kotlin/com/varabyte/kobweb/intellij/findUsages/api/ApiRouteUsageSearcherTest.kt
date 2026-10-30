@file:Suppress("FunctionName", "RedundantSuspendModifier")

package com.varabyte.kobweb.intellij.findUsages.api

import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import com.varabyte.kobweb.intellij.test.util.findUsages
import com.varabyte.truthish.assertThat
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class ApiRouteUsageSearcherTest : KobwebFullStackTestCase() {
    private lateinit var helloApi: KtNamedFunction
    private lateinit var userApi: KtNamedFunction
    private lateinit var meApi: KtNamedFunction
    private lateinit var chatStream: KtProperty

    override fun setUp() {
        super.setUp()

        helloApi = addFileToProject(
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
        ).findDescendantOfType<KtNamedFunction>()!!

        userApi = addFileToProject(
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
        ).findDescendantOfType<KtNamedFunction>()!!

        meApi = addFileToProject(
            Platform.Jvm,
            "api/users/Me.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api
            suspend fun me(ctx: ApiContext) {
            }
            """.trimIndent()
        ).findDescendantOfType<KtNamedFunction>()!!

        chatStream = addFileToProject(
            Platform.Jvm,
            "api/Chat.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.stream.ApiStream

            val chat = ApiStream { ctx -> }
            """.trimIndent()
        ).findDescendantOfType<KtProperty>()!!

        addFileToProject(
            Platform.Js,
            "components/Client.kt",
            // language=kotlin
            $$"""
            package $$TEST_GROUP.components

            import com.varabyte.kobweb.browser.api
            import com.varabyte.kobweb.streams.ApiStream
            import com.varabyte.kobweb.streams.rememberApiStream
            import kotlinx.browser.window

            suspend fun fetch(id: String) {
                window.api.get("hello")
                window.api.post("/hello?name=bob")
                window.api.get("users/bob")
                window.api.get("users/$id")
                window.api.get("users/me")

                // Not API paths
                println("hello")
                window.api.get("chat")
            }

            fun connect() {
                ApiStream("chat")
                ApiStream("/chat")

                // Not stream paths
                println("chat")
                ApiStream("hello")
            }
            """.trimIndent()
        )
    }

    fun testApiUsages() {
        assertThat(project.findUsages(helloApi)).containsExactly(
            "Client.kt: window.api.get(\"[hello]\")",
            "Client.kt: window.api.post(\"[/hello]?name=bob\")",
        )
    }

    fun testDynamicApiUsages() {
        assertThat(project.findUsages(userApi)).containsExactly(
            "Client.kt: window.api.get(\"[users/bob]\")",
            $$"Client.kt: window.api.get(\"[users/$id]\")",
        )
    }

    fun testStaticApiUsagesWinOverDynamicOnes() {
        assertThat(project.findUsages(meApi)).containsExactly(
            "Client.kt: window.api.get(\"[users/me]\")",
        )
    }

    fun testStreamUsages() {
        assertThat(project.findUsages(chatStream)).containsExactly(
            "Client.kt: ApiStream(\"[chat]\")",
            "Client.kt: ApiStream(\"[/chat]\")",
        )
    }
}
