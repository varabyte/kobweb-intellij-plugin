@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.completion.route

import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

class RouteParamCompletionTest : KobwebApplicationTestCase() {
    /**
     * Runs basic completion on [code] (with a `<caret>`) in a file called [fileName], and returns the suggestions, or
     * null if a single suggestion was inserted right away.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun complete(fileName: String, @Language("kotlin") code: String): List<String>? {
        myFixture.configureByText(fileName, code)
        return allowAnalysisOnEdt { myFixture.completeBasic() }?.map { it.lookupString }
    }

    fun testDynamicSegmentsAreSuggestedInRouteOrder() {
        val suggestions = complete(
            "Post.kt",
            // language=kotlin
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

        assertThat(suggestions!!).containsExactly("user", "post").inOrder()
    }

    fun testInferredSegmentNameIsSuggested() {
        val suggestions = complete(
            "PostId.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{}")
            @Composable
            fun PostPage(ctx: PageContext) {
                ctx.route.params["<caret>"]
            }
            """.trimIndent()
        )

        assertThat(suggestions!!).containsExactly("user", "post-id").inOrder()
    }

    fun testOptionalCatchAllSegmentIsSuggestedByName() {
        val suggestions = complete(
            "Docs.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{version}/{...rest?}")
            @Composable
            fun DocsPage(ctx: PageContext) {
                ctx.route.params["<caret>"]
            }
            """.trimIndent()
        )

        assertThat(suggestions!!).containsExactly("version", "rest").inOrder()
    }

    fun testPrefixFiltersSuggestions() {
        val suggestions = complete(
            "Post.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage(ctx: PageContext) {
                ctx.route.params["po<caret>"]
            }
            """.trimIndent()
        )

        // The only suggestion is inserted right away
        assertThat(suggestions).isNull()
        myFixture.checkResult(
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage(ctx: PageContext) {
                ctx.route.params["post<caret>"]
            }
            """.trimIndent()
        )
    }

    fun testDynamicParamsAndMapFunctionsAreSupported() {
        val suggestions = complete(
            "Post.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage(ctx: PageContext) {
                ctx.route.dynamicParams.getValue("<caret>")
            }
            """.trimIndent()
        )

        assertThat(suggestions!!).containsExactly("user", "post").inOrder()
    }

    fun testInCheckIsSupported() {
        val suggestions = complete(
            "Post.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage(ctx: PageContext) {
                "<caret>" in ctx.route.params
            }
            """.trimIndent()
        )

        assertThat(suggestions!!).containsExactly("user", "post").inOrder()
    }

    fun testInitRouteUsesThePageInTheSameFile() {
        val suggestions = complete(
            "Post.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.init.InitRoute
            import com.varabyte.kobweb.core.init.InitRouteContext

            @InitRoute
            fun initPostPage(ctx: InitRouteContext) {
                ctx.route.params["<caret>"]
            }

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage() {
            }
            """.trimIndent()
        )

        assertThat(suggestions!!).containsExactly("user", "post").inOrder()
    }

    fun testNothingIsSuggestedOutsideAPage() {
        val suggestions = complete(
            "Post.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage(ctx: PageContext) {
                PostTitle(ctx)
            }

            @Composable
            fun PostTitle(ctx: PageContext) {
                ctx.route.params["<caret>"]
            }
            """.trimIndent()
        )

        assertThat(suggestions.orEmpty()).containsNoneIn(listOf("user", "post"))
    }

    fun testOtherMapsNamedParamsGetNoSuggestions() {
        val suggestions = complete(
            "Post.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage() {
                val params = mapOf("a" to "b")
                params["<caret>"]
            }
            """.trimIndent()
        )

        assertThat(suggestions.orEmpty()).containsNoneIn(listOf("user", "post"))
    }
}
