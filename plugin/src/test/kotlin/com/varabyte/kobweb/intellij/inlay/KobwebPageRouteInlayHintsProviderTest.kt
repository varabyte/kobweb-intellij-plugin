package com.varabyte.kobweb.intellij.inlay

import org.intellij.lang.annotations.Language

class KobwebPageRouteInlayHintsProviderTest : KobwebInlayHintsTestBase() {
    override fun produceInlayProvider() = KobwebPageRouteInlayHintsProvider()

    private fun doTest(fileName: String, @Language("kotlin") code: String) {
        doTest(code, fileName = fileName)
    }

    fun testPageShowsRouteFromPackageAndFileName() {
        doTest(
            "BlogPost.kt",
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page/*<# /blog/blog-post #>*/
            @Composable
            fun BlogPostPage() {
            }
            """.trimIndent()
        )
    }

    fun testIndexPageShowsParentRoute() {
        doTest(
            "Index.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page/*<# / #>*/
            @Composable
            fun HomePage() {
            }
            """.trimIndent()
        )
    }

    fun testPageShowsRouteWithOverride() {
        doTest(
            "Post.kt",
            """
            package $TEST_GROUP.pages.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("{}")/*<# /posts/{post} #>*/
            @Composable
            fun PostPage() {
            }
            """.trimIndent()
        )
    }

    fun testPageShowsRouteWithPackageMapping() {
        myFixture.addFileToProject(
            "pages/users/PackageMapping.kt",
            // language=kotlin
            """
            @file:PackageMapping("{}")

            package $TEST_GROUP.pages.users

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent()
        )

        doTest(
            "Posts.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page/*<# /{users}/posts #>*/
            @Composable
            fun UserPostsPage() {
            }
            """.trimIndent()
        )
    }

    fun testHintIsShownAtEndOfLineWhenPageAnnotationSharesItsLine() {
        doTest(
            "About.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page @Composable/*<# /about #>*/
            fun AboutPage() {
            }
            """.trimIndent()
        )
    }

    fun testNoHintWhenOverrideIsTheFullRoute() {
        doTest(
            "About.kt",
            """
            package $TEST_GROUP.pages.company

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("/about")
            @Composable
            fun AboutPage() {
            }
            """.trimIndent()
        )
    }

    fun testNoHintForPagesKobwebSkips() {
        doTest(
            "Widget.kt",
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            // Outside the pages package, without an absolute route override
            @Page
            @Composable
            fun WidgetPage() {
            }

            // Inferred dynamic segments are only allowed at the end
            @Page("/users/{}/posts")
            @Composable
            fun UserPostsPage() {
            }
            """.trimIndent()
        )
    }

    fun testNoHintWhenRouteOverrideIsNotALiteral() {
        doTest(
            "Post.kt",
            """
            package $TEST_GROUP.pages.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            const val POST_ROUTE = "{}"

            @Page(POST_ROUTE)
            @Composable
            fun PostPage() {
            }
            """.trimIndent()
        )
    }

    fun testNoHintForFunctionsThatAreNotPages() {
        doTest(
            "About.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable

            @Composable
            fun AboutWidget() {
            }
            """.trimIndent()
        )
    }
}
