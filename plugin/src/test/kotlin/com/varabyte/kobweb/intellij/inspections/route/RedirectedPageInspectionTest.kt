@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.openapi.fileEditor.FileEditorManager
import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.test.util.selectedTextWithoutIndent
import com.varabyte.truthish.assertThat

class RedirectedPageInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = RedirectedPageInspection()

    override fun setUp() {
        super.setUp()

        myFixture.addFileToProject(
            ".kobweb/conf.yaml",
            // language=yaml
            """
            server:
              redirects:
                - from: "/blog/older-post"
                  to: "/blog/old-post"
                - from: "/blog/old-post"
                  to: "/blog/post"
                - from: "/blog/users/(.+)"
                  to: "/users/$1"
            """.trimIndent()
        )
    }

    fun testPagesWhoseRouteIsRedirectedAreFlagged() {
        doTest(
            fileName = "OldPost.kt",
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun <warning descr="This page is never shown, as a redirect in .kobweb/conf.yaml sends its route (\"/blog/old-post\") to \"/blog/post\".">OldPostPage</warning>() {
            }
            """.trimIndent(),
        )
    }

    fun testGoToRedirectSelectsTheRedirect() {
        doTest(
            fileName = "OldPost.kt",
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun <warning descr="This page is never shown, as a redirect in .kobweb/conf.yaml sends its route (\"/blog/old-post\") to \"/blog/post\".">OldPostPage</warning>() {
            }
            """.trimIndent(),
        )

        myFixture.launchAction(myFixture.getAllQuickFixes().single { it.text == "Go to redirect in .kobweb/conf.yaml" })

        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        assertThat(editor.virtualFile!!.path).isEqualTo("/src/.kobweb/conf.yaml")
        assertThat(editor.selectedTextWithoutIndent).isEqualTo(
            """
            from: "/blog/old-post"
            to: "/blog/post"
            """.trimIndent()
        )
    }

    fun testGoToRedirectSelectsTheFirstRedirectInAChain() {
        doTest(
            fileName = "OlderPost.kt",
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun <warning descr="This page is never shown, as a redirect in .kobweb/conf.yaml sends its route (\"/blog/older-post\") to \"/blog/post\".">OlderPostPage</warning>() {
            }
            """.trimIndent(),
        )

        myFixture.launchAction(myFixture.getAllQuickFixes().single { it.text == "Go to redirect in .kobweb/conf.yaml" })

        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        assertThat(editor.selectedTextWithoutIndent).isEqualTo(
            """
            from: "/blog/older-post"
            to: "/blog/old-post"
            """.trimIndent()
        )
    }

    fun testPagesWhoseRouteIsntRedirectedAreNotFlagged() {
        doTest(
            fileName = "Post.kt",
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun PostPage() {
            }
            """.trimIndent(),
        )
    }

    fun testPagesWithDynamicRoutesAreNotChecked() {
        doTest(
            fileName = "Users.kt",
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            // Only some users might be redirected, e.g. "/blog/users/bob"
            @Page("users/{user}")
            @Composable
            fun UserPage() {
            }
            """.trimIndent(),
        )
    }
}
