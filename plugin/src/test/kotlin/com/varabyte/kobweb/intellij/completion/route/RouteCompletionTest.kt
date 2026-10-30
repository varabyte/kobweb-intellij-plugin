@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.completion.route

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PsiTestUtil
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

class RouteCompletionTest : KobwebApplicationTestCase() {
    override fun setUp() {
        super.setUp()

        myFixture.addFileToProject(
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
        )

        myFixture.addFileToProject(
            "pages/blog/Index.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun BlogPage() {
            }
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "pages/users/Posts.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("{user}/posts")
            @Composable
            fun UserPostsPage() {
            }
            """.trimIndent()
        )
    }

    @Language("kotlin")
    private fun produceNav(link: String) = """
        package $TEST_GROUP.components

        import androidx.compose.runtime.Composable
        import com.varabyte.kobweb.silk.components.navigation.Link

        @Composable
        fun Nav() {
            Link("$link")
        }
    """.trimIndent()

    /**
     * Runs basic completion on [code] (with a `<caret>`) and returns the suggestions, or null if a single suggestion
     * was inserted right away.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun complete(@Language("kotlin") code: String): List<String>? {
        myFixture.configureByText("Nav.kt", code)
        return allowAnalysisOnEdt { myFixture.completeBasic() }?.map { it.lookupString }
    }

    fun testAllRoutesAreSuggestedInEmptyString() {
        assertThat(complete(produceNav("<caret>"))!!).containsExactly(
            "/blog/",
            "/blog/post",
            "/users/{user}/posts",
        ).inOrder()
    }

    fun testPagesKobwebRejectsAreNotSuggested() {
        myFixture.addFileToProject(
            "pages/About.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("about//us")
            @Composable
            fun AboutPage() {
            }
            """.trimIndent()
        )

        assertThat(complete(produceNav("<caret>"))!!).containsExactly(
            "/blog/",
            "/blog/post",
            "/users/{user}/posts",
        ).inOrder()
    }

    fun testRoutesMatchingWhatsTypedAreSuggested() {
        assertThat(complete(produceNav("/bl<caret>"))!!).containsExactly("/blog/", "/blog/post").inOrder()
    }

    fun testSingleMatchIsInserted() {
        assertThat(complete(produceNav("/blog/po<caret>"))).isNull()
        myFixture.checkResult(produceNav("/blog/post<caret>"))
    }

    fun testDynamicSegmentsAreFilledInWithTemplate() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)

        assertThat(complete(produceNav("/us<caret>"))).isNull()
        val templateState = TemplateManagerImpl.getTemplateState(myFixture.editor)!!
        myFixture.type("bob")
        templateState.nextTab()

        myFixture.checkResult(produceNav("/users/bob/posts<caret>"))
    }

    fun testRelativePathsAreNotCompleted() {
        assertThat(complete(produceNav("bl<caret>")).orEmpty()).isEmpty()
    }

    fun testStringsComparedWithRoutePathPropertiesAreCompleted() {
        assertThat(
            complete(
                """
                package $TEST_GROUP.components

                import com.varabyte.kobweb.core.RoutePath

                class CurrentRoute(@property:RoutePath val path: String)

                fun isBlog(route: CurrentRoute) = route.path == "/bl<caret>"
                """.trimIndent()
            )!!
        ).containsExactly("/blog/", "/blog/post").inOrder()
    }

    fun testStringsAssignedToRoutePathPropertiesAreCompleted() {
        assertThat(
            complete(
                """
                package $TEST_GROUP.components

                import com.varabyte.kobweb.navigation.Router

                fun interceptRoutes(router: Router) {
                    router.addRouteInterceptor {
                        path = "/bl<caret>"
                    }
                }
                """.trimIndent()
            )!!
        ).containsExactly("/blog/", "/blog/post").inOrder()
    }

    fun testStringsNotPassedToRouteParametersAreNotCompleted() {
        assertThat(
            complete(
                """
                package $TEST_GROUP.components

                fun log() {
                    println("/bl<caret>")
                }
                """.trimIndent()
            ).orEmpty()
        ).isEmpty()
    }

    fun testPublicFilesAreSuggestedAfterPages() {
        val resourcesRoot = myFixture.addFileToProject("resources/public/blog/banner.png", "")
            .virtualFile.parent.parent.parent
        myFixture.addFileToProject("resources/public/resume.pdf", "")
        PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeSourceRoot(module, resourcesRoot) }

        assertThat(complete(produceNav("<caret>"))!!).containsExactly(
            "/blog/",
            "/blog/post",
            "/users/{user}/posts",
            "/blog/banner.png",
            "/resume.pdf",
        ).inOrder()
    }

    fun testPublicFilesAreNotSuggestedWhereOnlyPagesAreAllowed() {
        val resourcesRoot = myFixture.addFileToProject("resources/public/resume.pdf", "").virtualFile.parent.parent
        PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeSourceRoot(module, resourcesRoot) }

        assertThat(
            complete(
                """
                package $TEST_GROUP.components

                import com.varabyte.kobweb.navigation.Router

                fun navigate(router: Router) {
                    router.tryRoutingTo("<caret>")
                }
                """.trimIndent()
            )!!
        ).containsExactly(
            "/blog/",
            "/blog/post",
            "/users/{user}/posts",
        ).inOrder()
    }

    fun testPagesAreNotSuggestedWherePagesArentAllowed() {
        val resourcesRoot = myFixture.addFileToProject("resources/public/blog/banner.png", "")
            .virtualFile.parent.parent.parent
        myFixture.addFileToProject("resources/public/resume.pdf", "")
        PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeSourceRoot(module, resourcesRoot) }

        assertThat(
            complete(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.silk.components.graphics.Image

                @Composable
                fun Banner() {
                    Image("<caret>")
                }
                """.trimIndent()
            )!!
        ).containsExactly(
            "/blog/banner.png",
            "/resume.pdf",
        ).inOrder()
    }

    fun testFilesKobwebGeneratesAreNotSuggested() {
        val resourcesRoot = myFixture.addFileToProject("resources/public/resume.pdf", "").virtualFile.parent.parent
        myFixture.addFileToProject("resources/public/_kobweb/fonts/font.woff2", "")
        PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeSourceRoot(module, resourcesRoot) }

        assertThat(complete(produceNav("/re<caret>"))).isNull()
        myFixture.checkResult(produceNav("/resume.pdf<caret>"))
        assertThat(complete(produceNav("/_k<caret>")).orEmpty()).isEmpty()
    }
}
