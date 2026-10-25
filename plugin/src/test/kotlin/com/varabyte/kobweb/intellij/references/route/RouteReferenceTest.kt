@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.references.route

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
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

    /** Marks `resources/` as a resources root, so that the files in `resources/public/` are served by Kobweb. */
    private fun addResourcesRoot() {
        val resourcesRoot = myFixture.tempDirFixture.findOrCreateDir("resources")
        PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeSourceRoot(module, resourcesRoot) }
    }

    /**
     * Returns the path (within `resources/public/`) of the public file or folder that the route segment at the caret
     * leads to, if any.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun resolvePublicFileAtCaret(@Language("kotlin") code: String): String? {
        myFixture.configureByText("Links.kt", code)
        val stringExpr = myFixture.file.findElementAt(myFixture.caretOffset)!!
            .parentOfType<KtStringTemplateExpression>(withSelf = true)!!
        val offsetInString = myFixture.caretOffset - stringExpr.textRange.startOffset
        val reference = stringExpr.references.filterIsInstance<PublicFileReference>()
            .singleOrNull { reference -> reference.rangeInElement.containsOffset(offsetInString) }
            ?: return null
        val publicFolder = myFixture.findFileInTempDir("resources/public")
        val file = allowAnalysisOnEdt { (reference.resolve() as? PsiFileSystemItem)?.virtualFile } ?: return null
        return VfsUtilCore.getRelativePath(file, publicFolder)
    }

    fun testRoutesToPublicFilesLeadToThem() {
        addResourcesRoot()
        myFixture.addFileToProject("resources/public/images/logo.png", "")

        fun produceNav(path: String) = """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("$path")
            }
        """.trimIndent()

        assertThat(resolvePublicFileAtCaret(produceNav("/images/lo<caret>go.png"))).isEqualTo("images/logo.png")
        // Each segment leads to its own folder
        assertThat(resolvePublicFileAtCaret(produceNav("/ima<caret>ges/logo.png"))).isEqualTo("images")
        assertThat(resolvePublicFileAtCaret(produceNav("/ima<caret>ges/"))).isEqualTo("images")
        assertThat(resolvePublicFileAtCaret(produceNav("/images/lo<caret>go.png?v=2"))).isEqualTo("images/logo.png")
        // Only paths that lead to a file lead to the folders along the way
        assertThat(resolvePublicFileAtCaret(produceNav("/ima<caret>ges/missing.png"))).isNull()
    }

    fun testRoutesToPagesDontLeadToPublicFiles() {
        addResourcesRoot()
        // Kobweb serves the page at "/blog/post" instead
        myFixture.addFileToProject("resources/public/blog/post", "")

        assertThat(
            resolvePublicFileAtCaret(
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
        ).isNull()
    }

    fun testRenamingPublicFileUpdatesRoutes() {
        addResourcesRoot()
        val logoFile = myFixture.addFileToProject("resources/public/images/logo.png", "")
        myFixture.configureByText(
            "Links.kt",
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/images/logo.png")
                Link("/images/logo.png?v=2")
                // Not a route, so left alone
                println("/images/logo.png")
            }
            """.trimIndent()
        )

        myFixture.renameElement(logoFile, "brand.png")

        myFixture.checkResult(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/images/brand.png")
                Link("/images/brand.png?v=2")
                // Not a route, so left alone
                println("/images/logo.png")
            }
            """.trimIndent()
        )
    }

    fun testRenamingPublicFolderUpdatesRoutes() {
        addResourcesRoot()
        val imagesFolder = myFixture.addFileToProject("resources/public/images/logo.png", "").containingDirectory
        myFixture.configureByText(
            "Links.kt",
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/images/logo.png")
                Link("/images/")
            }
            """.trimIndent()
        )

        myFixture.renameElement(imagesFolder, "img")

        myFixture.checkResult(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/img/logo.png")
                Link("/img/")
            }
            """.trimIndent()
        )
    }

    fun testMovingPublicFileUpdatesRoutes() {
        addResourcesRoot()
        val logoFile = myFixture.addFileToProject("resources/public/images/logo.png", "")
        val iconsFolder = myFixture.psiManager.findDirectory(
            myFixture.tempDirFixture.findOrCreateDir("resources/public/brand/icons")
        )!!
        myFixture.configureByText(
            "Links.kt",
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/images/logo.png?v=2")
            }
            """.trimIndent()
        )

        // Moved directly, as `myFixture.moveFile` would try to open the image in an editor
        MoveFilesOrDirectoriesProcessor(project, arrayOf(logoFile), iconsFolder, false, false, null, null).run()

        myFixture.checkResult(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/brand/icons/logo.png?v=2")
            }
            """.trimIndent()
        )
    }

    fun testMovingPublicFileOutOfPublicFolderDoesNotChangeRoutes() {
        addResourcesRoot()
        val logoFile = myFixture.addFileToProject("resources/public/images/logo.png", "")
        val archiveFolder = myFixture.psiManager.findDirectory(
            myFixture.tempDirFixture.findOrCreateDir("resources/archive")
        )!!
        @Language("kotlin")
        val code = """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/images/logo.png")
            }
        """.trimIndent()
        myFixture.configureByText("Links.kt", code)

        MoveFilesOrDirectoriesProcessor(project, arrayOf(logoFile), archiveFolder, false, false, null, null).run()

        // Kobweb doesn't serve it anymore, so there's no route to update to
        myFixture.checkResult(code)
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

    fun testAssignmentsToRoutePathPropertiesLeadToPages() {
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                import com.varabyte.kobweb.core.RoutePath

                class Redirect {
                    @set:RoutePath
                    var target = ""
                }

                fun redirect(redirect: Redirect) {
                    redirect.target = "/blog/po<caret>st"
                }
                """.trimIndent()
            )
        ).isEqualTo("BlogPostPage")

        // The version of Kobweb we test against doesn't annotate `RouteInterceptorScope.path` yet, but we recognize it
        // anyway
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                import com.varabyte.kobweb.navigation.Router

                fun interceptRoutes(router: Router) {
                    router.addRouteInterceptor {
                        path = "/blog/po<caret>st"
                    }
                }
                """.trimIndent()
            )
        ).isEqualTo("BlogPostPage")
    }

    fun testInitialValuesOfRoutePathDeclarationsLeadToPages() {
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                import com.varabyte.kobweb.core.RoutePath

                @RoutePath
                val blogPath = "/blog/po<caret>st"
                """.trimIndent()
            )
        ).isEqualTo("BlogPostPage")

        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.RoutePath

                @Composable
                fun NavItem(@RoutePath target: String = "/blog/po<caret>st") {
                }
                """.trimIndent()
            )
        ).isEqualTo("BlogPostPage")

        // Not annotated
        assertThat(
            resolveRouteAtCaret(
                """
                package $TEST_GROUP.components

                val blogPath = "/blog/po<caret>st"
                """.trimIndent()
            )
        ).isNull()
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
