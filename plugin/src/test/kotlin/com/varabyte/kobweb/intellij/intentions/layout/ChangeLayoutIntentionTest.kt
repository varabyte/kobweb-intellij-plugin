package com.varabyte.kobweb.intellij.intentions.layout

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModChooseAction
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.command.CommandProcessor
import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase
import com.varabyte.truthish.assertThat

class ChangeLayoutIntentionTest : KobwebIntentionTestBase() {
    override val intentionFamilyName = "Change layout…"

    override fun setUp() {
        super.setUp()

        myFixture.addFileToProject(
            "Layouts.kt",
            // language=kotlin
            """
            package $TEST_GROUP.components.layouts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.layout.Layout

            @Layout
            @Composable
            fun PageLayout(content: @Composable () -> Unit) {
                content()
            }

            @Layout
            @Composable
            fun BlogLayout(content: @Composable () -> Unit) {
                content()
            }
            """.trimIndent()
        )
    }

    fun testAvailableOnValidPath() {
        assertIntentionIsAvailable(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout(".components.layouts.Page<caret>Layout")
            @Composable
            fun Index() {
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableOnInvalidPath() {
        // Invalid paths are handled by a quick fix instead
        assertIntentionNotAvailable(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout(".components.layouts.Missing<caret>Layout")
            @Composable
            fun Index() {
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableOnUnrelatedStrings() {
        assertIntentionNotAvailable(
            """
            package $TEST_GROUP.pages

            val text = ".components.layouts.Page<caret>Layout"
            """.trimIndent()
        )
    }

    fun testChoosingLayoutListsCurrentLayoutFirst() {
        myFixture.configureByText(
            "Index.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout(".components.layouts.Page<caret>Layout")
            @Composable
            fun Index() {
            }
            """.trimIndent()
        )

        // Note: We call the intention directly here, because choosing from a list requires a popup, which tests can't
        // show
        val context = ActionContext.from(myFixture.editor, myFixture.file)
        val chooser = ChangeLayoutIntention().perform(context) as ModChooseAction
        val choices = chooser.actions().associateBy { it.getPresentation(context)!!.name() }

        assertThat(choices.keys)
            .containsExactly(".components.layouts.PageLayout", ".components.layouts.BlogLayout")
            .inOrder()
        // Executing a ModCommand must happen inside a command (but outside a write action)
        CommandProcessor.getInstance().executeCommand(project, {
            val blogLayoutChoice = choices.getValue(".components.layouts.BlogLayout")
            ModCommandExecutor.getInstance().executeInBatch(context, blogLayoutChoice.perform(context))
        }, null, null)

        myFixture.checkResult(
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout(".components.layouts.BlogLayout")
            @Composable
            fun Index() {
            }
            """.trimIndent()
        )
    }

    private fun addBlogDefaultLayout() {
        myFixture.addFileToProject(
            "pages/blog/DefaultLayout.kt",
            // language=kotlin
            """
            @file:Layout(".components.layouts.BlogLayout")

            package $TEST_GROUP.pages.blog

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )
    }

    fun testAvailableOnPageUsingDefaultLayout() {
        addBlogDefaultLayout()
        assertIntentionIsAvailable(
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun First<caret>PostPage() {
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableInPageBody() {
        addBlogDefaultLayout()
        assertIntentionNotAvailable(
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun FirstPostPage() {
                println("Hel<caret>lo")
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableOnPageWithoutDefaultLayout() {
        assertIntentionNotAvailable(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun Home<caret>Page() {
            }
            """.trimIndent()
        )
    }

    fun testChoosingLayoutForPageUsingDefaultLayoutAddsAnnotation() {
        addBlogDefaultLayout()
        myFixture.configureByText(
            "FirstPostPage.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun First<caret>PostPage() {
            }
            """.trimIndent()
        )

        val context = ActionContext.from(myFixture.editor, myFixture.file)
        val chooser = ChangeLayoutIntention().perform(context) as ModChooseAction
        val choices = chooser.actions().associateBy { it.getPresentation(context)!!.name() }

        // The page already uses `BlogLayout` by default, so it isn't offered
        assertThat(choices.keys).containsExactly(".components.layouts.PageLayout")
        // Executing a ModCommand must happen inside a command (but outside a write action)
        CommandProcessor.getInstance().executeCommand(project, {
            val pageLayoutChoice = choices.getValue(".components.layouts.PageLayout")
            ModCommandExecutor.getInstance().executeInBatch(context, pageLayoutChoice.perform(context))
        }, null, null)

        myFixture.checkResult(
            // language=kotlin
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun FirstPostPage() {
            }
            """.trimIndent()
        )
    }
}
