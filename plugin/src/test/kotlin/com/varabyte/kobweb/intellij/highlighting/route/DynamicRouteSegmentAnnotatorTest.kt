@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.highlighting.route

import com.varabyte.kobweb.intellij.highlighting.KobwebHighlightingColors
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

class DynamicRouteSegmentAnnotatorTest : KobwebApplicationTestCase() {
    override fun setUp() {
        super.setUp()

        myFixture.addFileToProject(
            "pages/author/Post.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.author

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("{author}/post/{post}")
            @Composable
            fun AuthorPostPage() {
            }
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "pages/docs/Path.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.docs

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("{...path}")
            @Composable
            fun DocsPage() {
            }
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "pages/About.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun AboutPage() {
            }
            """.trimIndent()
        )
    }

    /**
     * Highlights a file that links to [link], and returns each highlighted dynamic segment value with its tooltip, in
     * order, e.g. `"me" to "\"me\" fills the \"{author}\" segment in \"/author/{author}/post/{post}\"."`.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun highlightLink(link: String): List<Pair<String, String>> {
        @Language("kotlin")
        val code = """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav(id: String, version: String) {
                Link("$link")
            }
        """.trimIndent()
        myFixture.configureByText("Nav.kt", code)
        return allowAnalysisOnEdt { myFixture.doHighlighting() }
            .filter { info -> info.forcedTextAttributesKey == KobwebHighlightingColors.DYNAMIC_ROUTE_SEGMENT_VALUE }
            .map { info -> info.text to info.description }
    }

    private fun List<Pair<String, String>>.values() = this.map { (_, tooltip) -> tooltip }
    fun testValuesOfDynamicSegmentsAreHighlighted() {
        assertThat(highlightLink("/author/me/post/123?sort=new").values()).containsExactly(
            "\"me\" fills the \"{author}\" segment in \"/author/{author}/post/{post}\".",
            "\"123\" fills the \"{post}\" segment in \"/author/{author}/post/{post}\".",
        ).inOrder()
    }

    fun testCatchAllSegmentsHighlightEachValue() {
        assertThat(highlightLink("/docs/guides/setup")).containsExactly(
            // Each part of the value is highlighted separately, but they all fill the same segment
            "guides" to "\"guides/setup\" fills the \"{...path}\" segment in \"/docs/{...path}\".",
            "setup" to "\"guides/setup\" fills the \"{...path}\" segment in \"/docs/{...path}\".",
        ).inOrder()
    }

    fun testTemplateExpressionsAreNotHighlighted() {
        assertThat(highlightLink($$"/author/$id/post/123").values()).containsExactly(
            "\"123\" fills the \"{post}\" segment in \"/author/{author}/post/{post}\".",
        )
    }

    fun testCatchAllSegmentsTooltipIncludeTemplateExpressionsIfPresent() {
        assertThat(highlightLink($$"/docs/$version/guides/setup")).containsExactly(
            // $version is not highlighted itself but included in the tooltip for "guides" and "setup"
            "guides" to $$"\"$version/guides/setup\" fills the \"{...path}\" segment in \"/docs/{...path}\".",
            "setup" to $$"\"$version/guides/setup\" fills the \"{...path}\" segment in \"/docs/{...path}\".",
        ).inOrder()
    }

    fun testValuesWithEscapeSequencesAreHighlighted() {
        assertThat(highlightLink("/author/caf\\u00e9/post/123")).containsExactly(
            "caf\\u00e9" to "\"caf\\u00e9\" fills the \"{author}\" segment in \"/author/{author}/post/{post}\".",
            "123" to "\"123\" fills the \"{post}\" segment in \"/author/{author}/post/{post}\".",
        ).inOrder()
    }

    fun testStaticRoutesHaveNothingToHighlight() {
        assertThat(highlightLink("/about")).isEmpty()
    }
}
