package com.varabyte.kobweb.intellij.findUsages.route

import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.kobweb.intellij.test.util.findUsages
import com.varabyte.truthish.assertThat
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class PageRouteUsageSearcherTest : KobwebApplicationTestCase() {
    private lateinit var homePage: KtNamedFunction
    private lateinit var blogPostPage: KtNamedFunction
    private lateinit var postPage: KtNamedFunction

    override fun setUp() {
        super.setUp()

        homePage = myFixture.addFileToProject(
            "pages/Index.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun HomePage() {
            }
            """.trimIndent()
        ).findDescendantOfType<KtNamedFunction>()!!

        blogPostPage = myFixture.addFileToProject(
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
        ).findDescendantOfType<KtNamedFunction>()!!

        postPage = myFixture.addFileToProject(
            "pages/posts/Post.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("{}")
            @Composable
            fun PostPage() {
            }
            """.trimIndent()
        ).findDescendantOfType<KtNamedFunction>()!!

        myFixture.addFileToProject(
            "components/Nav.kt",
            // language=kotlin
            $$"""
            package $$TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.rememberPageContext
            import com.varabyte.kobweb.navigation.Anchor
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav(id: String) {
                Link("/")
                Link("/blog/post")
                Anchor("/blog/post#comments")
                Link("/posts/123")
                Link("/posts/$id")
                rememberPageContext().router.navigateTo("/posts/new")

                // Not routes
                println("/blog/post")
                Link("https://example.com/blog/post")
            }
            """.trimIndent()
        )
    }

    fun testStaticPageUsages() {
        assertThat(project.findUsages(blogPostPage)).containsExactly(
            "Nav.kt: Anchor(\"[/blog/post]#comments\")",
            "Nav.kt: Link(\"[/blog/post]\")",
        )
    }

    fun testDynamicPageUsages() {
        assertThat(project.findUsages(postPage)).containsExactly(
            "Nav.kt: Link(\"[/posts/123]\")",
            $$"Nav.kt: Link(\"[/posts/$id]\")",
            "Nav.kt: rememberPageContext().router.navigateTo(\"[/posts/new]\")",
        )
    }

    fun testRouteWithoutWordsUsages() {
        // "/" has no words to look up, so every string has to be checked
        assertThat(project.findUsages(homePage)).containsExactly(
            "Nav.kt: Link(\"[/]\")",
        )
    }

    fun testSearchWordComesFromTheLastStaticSegment() {
        assertThat(PageRouteUsageSearcher.findSearchWord("/blog/post")).isEqualTo("post")
        assertThat(PageRouteUsageSearcher.findSearchWord("/docs/{section}/advanced")).isEqualTo("advanced")
        assertThat(PageRouteUsageSearcher.findSearchWord("/users/{user}")).isEqualTo("users")
        // Index pages end with an empty segment
        assertThat(PageRouteUsageSearcher.findSearchWord("/blog/")).isEqualTo("blog")
    }

    fun testSearchWordIsLongestWordInSegment() {
        // "blog-post" is indexed as two words, "blog" and "post"
        assertThat(PageRouteUsageSearcher.findSearchWord("/our-team")).isEqualTo("team")
        assertThat(PageRouteUsageSearcher.findSearchWord("/release-notes")).isEqualTo("release")
    }

    fun testRoutesWithoutStaticWordsHaveNoSearchWord() {
        assertThat(PageRouteUsageSearcher.findSearchWord("/")).isNull()
        assertThat(PageRouteUsageSearcher.findSearchWord("/{slug}")).isNull()
        assertThat(PageRouteUsageSearcher.findSearchWord("/{a}/{b}/{c}")).isNull()
    }
}
