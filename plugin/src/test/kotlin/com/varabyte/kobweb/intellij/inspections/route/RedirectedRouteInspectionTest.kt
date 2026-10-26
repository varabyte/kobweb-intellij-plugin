@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.openapi.fileEditor.FileEditorManager
import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.route.ReplaceRedirectedRouteQuickFix
import com.varabyte.kobweb.intellij.test.util.selectedTextWithoutIndent
import com.varabyte.truthish.assertThat

class RedirectedRouteInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = RedirectedRouteInspection()

    override fun setUp() {
        super.setUp()

        myFixture.addFileToProject(
            ".kobweb/conf.yaml",
            // language=yaml
            """
            server:
              redirects:
                - from: "/old-post"
                  to: "/blog/post"
                - from: "/socials/facebook/([^/]+)"
                  to: "/socials/meta/$1"
                - from: "(/socials/meta)/about-facebook"
                  to: "$1/about-meta"
            """.trimIndent()
        )
    }

    fun testRedirectedRoutesAreFlagged() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("<weak_warning descr="This route may be out of date, as .kobweb/conf.yaml redirects it to \"/blog/post\".">/old-post</weak_warning>?ref=nav")
                Link("/blog/post")
                // Not a route
                println("/old-post")
            }
            """.trimIndent(),
            thenFixWith<ReplaceRedirectedRouteQuickFix>(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.silk.components.navigation.Link

                @Composable
                fun Nav() {
                    Link("/blog/post?ref=nav")
                    Link("/blog/post")
                    // Not a route
                    println("/old-post")
                }
                """.trimIndent()
            ),
        )
    }

    fun testGoToRedirectSelectsTheRedirect() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("<weak_warning descr="This route may be out of date, as .kobweb/conf.yaml redirects it to \"/blog/post\".">/old-post</weak_warning>")
            }
            """.trimIndent(),
        )

        myFixture.launchAction(myFixture.getAllQuickFixes().single { it.text == "Go to redirect in .kobweb/conf.yaml" })

        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        assertThat(editor.virtualFile!!.path).isEqualTo("/src/.kobweb/conf.yaml")
        assertThat(editor.selectedTextWithoutIndent).isEqualTo(
            """
            from: "/old-post"
            to: "/blog/post"
            """.trimIndent()
        )
    }

    fun testFixReplacesRoutesWithWhereAllRedirectsLead() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("<weak_warning descr="This route may be out of date, as .kobweb/conf.yaml redirects it to \"/socials/meta/about-meta\".">/socials/facebook/about-facebook</weak_warning>")
            }
            """.trimIndent(),
            thenFixWith<ReplaceRedirectedRouteQuickFix>(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.silk.components.navigation.Link

                @Composable
                fun Nav() {
                    Link("/socials/meta/about-meta")
                }
                """.trimIndent()
            ),
        )
    }

    fun testComparisonsWithRedirectedRoutesAreFlagged() {
        doTest(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.core.PageContext

            // Never true, as the current route is the one the visitor was redirected to
            fun isOldPost(ctx: PageContext) = ctx.route.path == "<weak_warning>/old-post</weak_warning>"
            """.trimIndent(),
        )
    }

    fun testRoutesWithUnknownSegmentsAreNotFlagged() {
        doTest(
            $$"""
            package $$TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav(user: String) {
                // Might be redirected, but there's no telling where to
                Link("/socials/facebook/$user")
            }
            """.trimIndent(),
        )
    }
}
