@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PsiTestUtil
import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import org.jetbrains.jps.model.java.JavaResourceRootType

class DisallowedRouteDestinationInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = DisallowedRouteDestinationInspection()

    override fun setUp() {
        super.setUp()

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

        // A catch-all page, which matches every path under "/images"
        myFixture.addFileToProject(
            "pages/images/Gallery.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.images

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("{...rest}")
            @Composable
            fun GalleryPage() {
            }
            """.trimIndent()
        )

        val resourcesRoot = myFixture.addFileToProject("resources/public/images/cat.png", "")
            .virtualFile.parent.parent.parent
        myFixture.addFileToProject("resources/public/resume.pdf", "")
        PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeSourceRoot(module, resourcesRoot) }
    }

    fun testPagesAreFlaggedWherePagesArentAllowed() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.graphics.Image

            @Composable
            fun Pictures() {
                Image("<warning descr="This route leads to a page, which isn't allowed here.">/about</warning>")
                // Only the catch-all page matches
                Image("<warning>/images/dog.png</warning>")
            }
            """.trimIndent(),
        )
    }

    fun testPublicFilesWinOverPagesWherePagesArentAllowed() {
        doTest(
            $$"""
            package $$TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.graphics.Image

            @Composable
            fun Pictures(name: String) {
                // The server serves the file, even though the catch-all page matches too
                Image("/images/cat.png")
                // Could be a file in the "images" folder
                Image("/images/$name")
            }
            """.trimIndent(),
        )
    }

    fun testResourcesAreFlaggedWhereOnlyPagesAreAllowed() {
        doTest(
            $$"""
            package $$TEST_GROUP.components

            import com.varabyte.kobweb.navigation.Router

            fun navigate(router: Router, name: String) {
                router.tryRoutingTo("/about")
                router.tryRoutingTo("<warning descr="This route leads to a resource, which isn't allowed here.">/resume.pdf</warning>")
                router.tryRoutingTo("<warning>/api/hello</warning>")
                // Kobweb's router shows the catch-all page, even though the file exists too
                router.tryRoutingTo("/images/cat.png")
                // Not known until runtime
                router.tryRoutingTo("/$name.pdf")
            }
            """.trimIndent(),
        )
    }

    fun testResourcesAreFlaggedInPathsSetByRouteInterceptors() {
        // The version of Kobweb we test against doesn't annotate `RouteInterceptorScope.path` yet, but we recognize it
        // anyway
        doTest(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.navigation.Router

            fun interceptRoutes(router: Router) {
                router.addRouteInterceptor {
                    if (path == "/old/resume") {
                        path = "<warning>/resume.pdf</warning>"
                    }
                }
            }
            """.trimIndent(),
        )
    }

    fun testPagesAndResourcesAreNotFlaggedWhereBothAreAllowed() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/about")
                Link("/resume.pdf")
            }
            """.trimIndent(),
        )
    }
}
