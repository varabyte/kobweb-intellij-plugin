@file:Suppress("FunctionName", "RedundantSuspendModifier")

package com.varabyte.kobweb.intellij.completion.route

import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

class ApiRouteParamCompletionTest : KobwebFullStackTestCase() {
    /**
     * Runs basic completion on [code] (with a `<caret>`) in a JVM file called [fileName], and returns the suggestions,
     * or null if a single suggestion was inserted right away.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun complete(fileName: String, @Language("kotlin") code: String): List<String>? {
        configureByText(Platform.Jvm, fileName, code)
        return allowAnalysisOnEdt { myFixture.completeBasic() }?.map { it.lookupString }
    }

    fun testDynamicSegmentsAreSuggestedInApiEndpoint() {
        val suggestions = complete(
            "api/users/Post.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api("{user}/posts/{}")
            suspend fun post(ctx: ApiContext) {
                ctx.req.params["<caret>"]
            }
            """.trimIndent()
        )

        assertThat(suggestions!!).containsExactly("user", "post").inOrder()
    }

    fun testNothingIsSuggestedOutsideAnApiEndpoint() {
        val suggestions = complete(
            "api/users/Post.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api("{user}/posts/{}")
            suspend fun post(ctx: ApiContext) {
                logUser(ctx)
            }

            fun logUser(ctx: ApiContext) {
                ctx.req.params["<caret>"]
            }
            """.trimIndent()
        )

        assertThat(suggestions.orEmpty()).containsNoneIn(listOf("user", "post"))
    }
}
