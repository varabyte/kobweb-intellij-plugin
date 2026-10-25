@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PsiTestUtil
import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.util.kobweb.KOBWEB_METADATA_MODULE
import org.jetbrains.jps.model.java.JavaResourceRootType

class UnknownRouteInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = UnknownRouteInspection()

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
    }

    fun testRoutesThatLeadToNoPageAreFlagged() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.navigation.Router
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav(router: Router) {
                Link("/blog/post")
                Link("/blog/post/")
                Link("<warning descr="This route doesn't correspond to any known page or public resource.">/blog/pots</warning>")
                router.navigateTo("<warning>/about</warning>?ref=nav")
            }
            """.trimIndent(),
        )
    }

    fun testRoutesWithUnknownSegmentsAreOnlyFlaggedIfNothingCouldMatch() {
        doTest(
            $$"""
            package $$TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav(id: String) {
                // "id" could be "new"
                Link("/posts/$id")
                Link("<warning>/pots/$id</warning>")
            }
            """.trimIndent(),
        )
    }

    fun testRoutesThatArentCheckedAreNotFlagged() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                // Not a route
                Link("https://example.com/missing")
                // Relative route
                Link("missing")
                // API routes are served by the backend
                Link("/api/missing")
                // Not passed to a parameter that takes a route
                println("/missing")
            }
            """.trimIndent(),
        )
    }

    fun testPublicResourcesAreNotFlagged() {
        val resourcesRoot = myFixture.addFileToProject("resources/public/resume.pdf", "")
            .virtualFile.parent.parent
        // Files Kobweb generates are public files too
        myFixture.addFileToProject("resources/public/_kobweb/fonts/font.woff2", "")
        PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeSourceRoot(module, resourcesRoot) }

        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/resume.pdf")
                Link("/_kobweb/fonts/font.woff2")
                Link("<warning>/missing.pdf</warning>")
            }
            """.trimIndent(),
        )
    }

    fun testCustomPublicPathIsUsed() {
        registerKobwebProject(publicPath = "static")
        val resourcesRoot = myFixture.addFileToProject("resources/static/resume.pdf", "")
            .virtualFile.parent.parent
        myFixture.addFileToProject("resources/public/old.pdf", "")
        PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeSourceRoot(module, resourcesRoot) }

        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/resume.pdf")
                // Kobweb doesn't serve the default public folder when the project uses a different one
                Link("<warning>/old.pdf</warning>")
            }
            """.trimIndent(),
        )
    }

    fun testPublicFoldersOutsideResourcesAreFlagged() {
        // In a source root, not a resources root, so Kobweb wouldn't serve it
        myFixture.addFileToProject("public/resume.pdf", "")

        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("<warning>/resume.pdf</warning>")
            }
            """.trimIndent(),
        )
    }

    fun testPublicResourcesFromKobwebLibrariesAreNotFlagged() {
        val libsDir = FileUtil.createTempDirectory("kobweb-libs", null)
        Disposer.register(testRootDisposable) { FileUtil.delete(libsDir) }
        libsDir.resolve("kobweb-lib/$KOBWEB_METADATA_MODULE").apply { parentFile.mkdirs() }.writeText("{}")
        libsDir.resolve("kobweb-lib/public/fonts/logo.woff2").apply { parentFile.mkdirs() }.writeText("")
        // Kobweb ignores public files in libraries that aren't Kobweb libraries
        libsDir.resolve("other-lib/public/other.woff2").apply { parentFile.mkdirs() }.writeText("")
        PsiTestUtil.addLibrary(testRootDisposable, module, "kobweb-lib", libsDir.path, "kobweb-lib")
        PsiTestUtil.addLibrary(testRootDisposable, module, "other-lib", libsDir.path, "other-lib")

        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/fonts/logo.woff2")
                Link("<warning>/other.woff2</warning>")
            }
            """.trimIndent(),
        )
    }

    fun testRoutePathParametersAreChecked() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.RoutePath

            @Composable
            fun NavItem(@RoutePath target: String) {
            }

            @Composable
            fun PlannedNavItem(@RoutePath(mustExist = false) target: String) {
            }

            @Composable
            fun Nav() {
                NavItem("<warning>/missing</warning>")
                PlannedNavItem("/missing")
            }
            """.trimIndent(),
        )
    }

    fun testComparisonsAndAssignmentsWithRoutePathPropertiesAreChecked() {
        doTest(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.core.RoutePath

            class Routes {
                @RoutePath
                val knownPath = "/blog/post"

                // The getter describes the value being compared, and the setter the value being assigned
                @get:RoutePath(mustExist = false)
                @set:RoutePath(mustExist = true)
                var currentPath = "/blog/post"
            }

            fun check(routes: Routes) {
                println(routes.knownPath == "/blog/post")
                println(routes.knownPath == "<warning>/missing</warning>")
                println(routes.currentPath == "/missing")
                routes.currentPath = "/blog/post"
                routes.currentPath = "<warning>/missing</warning>"
            }
            """.trimIndent(),
        )
    }

    fun testPathsSetByRouteInterceptorsAreChecked() {
        // The version of Kobweb we test against doesn't annotate `RouteInterceptorScope.path` yet, but we recognize it
        // anyway
        doTest(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.navigation.Router

            fun interceptRoutes(router: Router) {
                router.addRouteInterceptor {
                    // The current path might not lead anywhere, but the one it's redirected to should
                    if (path == "/old/blog/post") {
                        path = "/blog/post"
                    }
                    if (path == "/old/missing") {
                        path = "<warning>/missing</warning>"
                    }
                }
            }
            """.trimIndent(),
        )
    }

    fun testInitialValuesOfRoutePathDeclarationsAreChecked() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.RoutePath

            @RoutePath
            val blogPath = "/blog/post"

            @RoutePath
            val missingPath = "<warning>/missing</warning>"

            class Routes {
                // The setter describes values written into the property, which includes its initial value
                @get:RoutePath(mustExist = false)
                @set:RoutePath(mustExist = true)
                var currentPath = "<warning>/missing</warning>"
            }

            @Composable
            fun NavItem(@RoutePath target: String = "<warning>/missing</warning>") {
            }

            @Composable
            fun PlannedNavItem(@RoutePath(mustExist = false) target: String = "/missing") {
            }

            class NavEntry(
                @property:RoutePath val target: String = "<warning>/missing</warning>",
                @set:RoutePath var redirect: String = "<warning>/missing</warning>",
                val label: String = "/missing",
            )
            """.trimIndent(),
        )
    }
}
