@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.references.route

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.PsiTestUtil
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class RouteReferenceTest : KobwebApplicationTestCase() {
    private lateinit var blogPostPage: KtNamedFunction

    override fun setUp() {
        super.setUp()

        // Newer versions of Kobweb have this annotation, but the version we test against doesn't
        myFixture.addFileToProject(
            "RoutePath.kt",
            // language=kotlin
            """
            package com.varabyte.kobweb.core

            enum class RoutePathType { URL, PATH_QUERY_AND_FRAGMENT, PATH_ONLY }

            @Target(
                AnnotationTarget.VALUE_PARAMETER,
                AnnotationTarget.PROPERTY,
                AnnotationTarget.PROPERTY_SETTER,
                AnnotationTarget.PROPERTY_GETTER,
            )
            annotation class RoutePath(val type: RoutePathType = RoutePathType.URL, val mustExist: Boolean = true)
            """.trimIndent()
        )

        myFixture.addFileToProject(
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
        )

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

        myFixture.addFileToProject(
            "pages/posts/New.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun NewPostPage() {
            }
            """.trimIndent()
        )

        myFixture.addFileToProject(
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
        )
    }

    /**
     * Returns the name of the page that the route string at the caret leads to, if any.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun resolveRouteAtCaret(@Language("kotlin") code: String): String? {
        myFixture.configureByText("Links.kt", code)
        val stringExpr = myFixture.file.findElementAt(myFixture.caretOffset)!!
            .parentOfType<KtStringTemplateExpression>(withSelf = true)!!
        val reference = stringExpr.references.filterIsInstance<RouteReference>().singleOrNull() ?: return null
        return allowAnalysisOnEdt { (reference.resolve() as? KtNamedFunction)?.name }
    }

    fun testLinkLeadsToPage() {
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.silk.components.navigation.Link

                @Composable
                fun Nav() {
                    Link("/blog/po<caret>st")
                }
                """.trimIndent()
            )
        ).isEqualTo("BlogPostPage")
    }

    fun testAnchorLeadsToPage() {
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.navigation.Anchor

                @Composable
                fun Nav() {
                    Anchor("<caret>/")
                }
                """.trimIndent()
            )
        ).isEqualTo("HomePage")
    }

    fun testNavigateToLeadsToDynamicPage() {
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.rememberPageContext

                @Composable
                fun Nav() {
                    val ctx = rememberPageContext()
                    ctx.router.navigateTo("/posts/12<caret>3")
                }
                """.trimIndent()
            )
        ).isEqualTo("PostPage")
    }

    fun testStaticRouteWinsOverDynamicRoute() {
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.silk.components.navigation.Link

                @Composable
                fun Nav() {
                    Link("/posts/n<caret>ew")
                }
                """.trimIndent()
            )
        ).isEqualTo("NewPostPage")
    }

    fun testTemplateExpressionsOnlyLeadToDynamicRoutes() {
        assertThat(
            resolveRouteAtCaret(
                $$"""
                package $$TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.silk.components.navigation.Link

                @Composable
                fun Nav(id: String) {
                    Link("/po<caret>sts/$id")
                }
                """.trimIndent()
            )
        ).isEqualTo("PostPage")
    }

    fun testQueryParametersAreIgnored() {
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.silk.components.navigation.Link

                @Composable
                fun Nav() {
                    Link("/blog/po<caret>st?comments=true")
                }
                """.trimIndent()
            )
        ).isEqualTo("BlogPostPage")
    }

    fun testStringsThatArentRoutesDontLeadToPages() {
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                fun log() {
                    println("/blog/po<caret>st")
                }
                """.trimIndent()
            )
        ).isNull()
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun testRoutesToPublicFilesLeadToThem() {
        val resourcesRoot = myFixture.addFileToProject("resources/public/favicon.ico", "").virtualFile.parent.parent
        PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeSourceRoot(module, resourcesRoot) }

        myFixture.configureByText(
            "Links.kt",
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/favi<caret>con.ico")
            }
            """.trimIndent()
        )
        val stringExpr = myFixture.file.findElementAt(myFixture.caretOffset)!!
            .parentOfType<KtStringTemplateExpression>(withSelf = true)!!
        val reference = stringExpr.references.filterIsInstance<RouteReference>().single()

        assertThat(allowAnalysisOnEdt { (reference.resolve() as? PsiFile)?.virtualFile?.path })
            .isEqualTo(resourcesRoot.path + "/public/favicon.ico")
    }

    fun testParametersAnnotatedWithRoutePathTakeRoutes() {
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.RoutePath

                @Composable
                fun NavItem(@RoutePath target: String) {
                }

                @Composable
                fun Nav() {
                    NavItem("/blog/po<caret>st")
                }
                """.trimIndent()
            )
        ).isEqualTo("BlogPostPage")
    }

    fun testComparisonsWithRoutePathPropertiesLeadToPages() {
        @Language("kotlin")
        fun produceNav(comparison: String) = """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.core.RoutePath

            class CurrentRoute(@property:RoutePath val path: String, val title: String)

            fun isActive(route: CurrentRoute): Boolean {
                return $comparison
            }
        """.trimIndent()

        assertThat(resolveRouteAtCaret(produceNav("route.path == \"/blog/po<caret>st\""))).isEqualTo("BlogPostPage")
        assertThat(resolveRouteAtCaret(produceNav("\"/blog/po<caret>st\" != route.path"))).isEqualTo("BlogPostPage")
        // Not a property that holds a route
        assertThat(resolveRouteAtCaret(produceNav("route.title == \"/blog/po<caret>st\""))).isNull()
    }

    fun testComparisonsWithCurrentRouteLeadToPages() {
        // The version of Kobweb we test against doesn't annotate `ctx.route.path` yet, but we recognize it anyway
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.rememberPageContext

                @Composable
                fun Nav() {
                    val ctx = rememberPageContext()
                    println(ctx.route.path == "/blog/po<caret>st")
                }
                """.trimIndent()
            )
        ).isEqualTo("BlogPostPage")
    }

    fun testWhenBranchesOnRoutePathPropertiesLeadToPages() {
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                import com.varabyte.kobweb.core.RoutePath

                class CurrentRoute(@property:RoutePath val path: String)

                fun titleFor(route: CurrentRoute): String {
                    return when (route.path) {
                        "/" -> "Home"
                        "/blog/po<caret>st" -> "Blog"
                        else -> "Unknown"
                    }
                }
                """.trimIndent()
            )
        ).isEqualTo("BlogPostPage")
    }

    fun testRenamingPageDoesNotChangeRoutes() {
        @Language("kotlin")
        val code = """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/blog/post")
            }
        """.trimIndent()
        myFixture.configureByText("Links.kt", code)

        myFixture.renameElement(blogPostPage, "RenamedBlogPostPage")

        // A page's route comes from its file and package, not its function's name
        myFixture.checkResult(code)
    }
}
