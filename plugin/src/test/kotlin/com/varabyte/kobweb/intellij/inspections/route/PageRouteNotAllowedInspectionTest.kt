@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PsiTestUtil
import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import org.jetbrains.jps.model.java.JavaResourceRootType

class PageRouteNotAllowedInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = PageRouteNotAllowedInspection()

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
                Image("<warning descr="This route leads to a page, but pages aren't allowed here.">/about</warning>")
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

    fun testPagesAreNotFlaggedWherePagesAreAllowed() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/about")
            }
            """.trimIndent(),
        )
    }
}
