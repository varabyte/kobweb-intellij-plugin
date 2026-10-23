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

    fun testRouteWithSeveralWordsInOneSegmentUsages() {
        // IntelliJ indexes "release-notes" as two words, "release" and "notes", rather than as one
        val releaseNotesPage = myFixture.addFileToProject(
            "pages/ReleaseNotes.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun ReleaseNotesPage() {
            }
            """.trimIndent()
        ).findDescendantOfType<KtNamedFunction>()!!

        myFixture.addFileToProject(
            "components/Footer.kt",
            // language=kotlin
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Footer() {
                Link("/release-notes")
            }
            """.trimIndent()
        )

        assertThat(project.findUsages(releaseNotesPage)).containsExactly(
            "Footer.kt: Link(\"[/release-notes]\")",
        )
    }

    fun testRouteWithoutWordsUsages() {
        // "/" has no static segments to look up, so every string has to be checked
        assertThat(project.findUsages(homePage)).containsExactly(
            "Nav.kt: Link(\"[/]\")",
        )
    }
}
