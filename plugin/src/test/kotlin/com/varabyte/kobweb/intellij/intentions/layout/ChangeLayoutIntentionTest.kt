package com.varabyte.kobweb.intellij.intentions.layout

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModChooseAction
import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase
import com.varabyte.kobweb.intellij.test.util.choicesByName
import com.varabyte.kobweb.intellij.test.util.performAndExecute
import com.varabyte.truthish.assertThat

class ChangeLayoutIntentionTest : KobwebIntentionTestBase() {
    override val intentionFamilyName = "Change layout…"

    private fun addPageLayout() {
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
            """.trimIndent()
        )
    }

    private fun addPageAndBlogLayouts() {
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
        addPageAndBlogLayouts()
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
        addPageAndBlogLayouts()
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

    fun testAvailableOnLayoutAnnotation() {
        addPageAndBlogLayouts()
        assertIntentionIsAvailable(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Lay<caret>out(".components.layouts.PageLayout")
            @Composable
            fun Index() {
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableOnLayoutAnnotationWithInvalidPath() {
        addPageAndBlogLayouts()
        // Invalid paths are handled by a quick fix instead
        assertIntentionNotAvailable(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Lay<caret>out(".components.layouts.MissingLayout")
            @Composable
            fun Index() {
            }
            """.trimIndent()
        )
    }

    fun testChoosingLayoutFromAnnotationReplacesPath() {
        addPageAndBlogLayouts()
        myFixture.configureByText(
            "Index.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Lay<caret>out(".components.layouts.PageLayout")
            @Composable
            fun Index() {
            }
            """.trimIndent()
        )

        val context = ActionContext.from(myFixture.editor, myFixture.file)
        val chooser = ChangeLayoutIntention().perform(context) as ModChooseAction
        val choices = chooser.choicesByName(context)

        assertThat(choices.keys)
            .containsExactly(".components.layouts.PageLayout", ".components.layouts.BlogLayout")
            .inOrder()
        choices.getValue(".components.layouts.BlogLayout").performAndExecute(context)

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

    fun testNotAvailableOnUnrelatedStrings() {
        addPageAndBlogLayouts()
        assertIntentionNotAvailable(
            """
            package $TEST_GROUP.pages

            val text = ".components.layouts.Page<caret>Layout"
            """.trimIndent()
        )
    }

    fun testChoosingLayoutListsCurrentLayoutFirst() {
        addPageAndBlogLayouts()
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
        val choices = chooser.choicesByName(context)

        assertThat(choices.keys)
            .containsExactly(".components.layouts.PageLayout", ".components.layouts.BlogLayout")
            .inOrder()
        choices.getValue(".components.layouts.BlogLayout").performAndExecute(context)

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
        addPageAndBlogLayouts()
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
        addPageAndBlogLayouts()
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

    fun testOfferedAsChooseLayoutOnPageWithoutLayout() {
        addPageAndBlogLayouts()
        myFixture.configureByText(
            "HomePage.kt",
            // language=kotlin
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

        // The page doesn't have a layout yet, so there's nothing to change
        val intention = myFixture.availableIntentions.single { it.familyName == "Change layout…" }
        assertThat(intention.text).isEqualTo("Choose layout…")
    }

    fun testAvailableOnPageAnnotation() {
        addPageAndBlogLayouts()
        assertIntentionIsAvailable(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Pa<caret>ge
            @Composable
            fun HomePage() {
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableOnPageLayoutAnnotationWithoutPath() {
        // A page's `@Layout` must have a path, which a quick fix handles instead
        addPageAndBlogLayouts()
        myFixture.configureByText(
            "HomePage.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Lay<caret>out
            @Composable
            fun HomePage() {
            }
            """.trimIndent()
        )

        assertThat(myFixture.availableIntentions.map { it.familyName }).doesNotContain("Change layout…")
    }

    fun testChoosingLayoutForPageWithoutLayoutAddsAnnotation() {
        addPageAndBlogLayouts()
        myFixture.configureByText(
            "HomePage.kt",
            // language=kotlin
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

        val context = ActionContext.from(myFixture.editor, myFixture.file)
        val chooser = ChangeLayoutIntention().perform(context) as ModChooseAction
        val choices = chooser.choicesByName(context)

        assertThat(choices.keys)
            .containsExactly(".components.layouts.BlogLayout", ".components.layouts.PageLayout")
            .inOrder()
        choices.getValue(".components.layouts.PageLayout").performAndExecute(context)

        myFixture.checkResult(
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun HomePage() {
            }
            """.trimIndent()
        )
    }

    fun testChoosingLayoutFromHeaderOfPageWithLayoutUpdatesIt() {
        addPageAndBlogLayouts()
        myFixture.configureByText(
            "HomePage.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun Home<caret>Page() {
            }
            """.trimIndent()
        )

        val context = ActionContext.from(myFixture.editor, myFixture.file)
        val chooser = ChangeLayoutIntention().perform(context) as ModChooseAction
        val choices = chooser.choicesByName(context)

        assertThat(choices.keys)
            .containsExactly(".components.layouts.PageLayout", ".components.layouts.BlogLayout")
            .inOrder()
        choices.getValue(".components.layouts.BlogLayout").performAndExecute(context)

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
            fun HomePage() {
            }
            """.trimIndent()
        )
    }

    fun testOfferedAsChooseLayoutInsteadOfChangeLayoutOnPageWithNoLayout() {
        addPageAndBlogLayouts()
        addBlogDefaultLayout()
        myFixture.configureByText(
            "FirstPostPage.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            @NoLayout
            @Composable
            fun First<caret>PostPage() {
            }
            """.trimIndent()
        )

        val intention = myFixture.availableIntentions.single { it.familyName == "Change layout…" }
        assertThat(intention.text).isEqualTo("Choose layout…")
    }

    fun testChoosingLayoutForPageWithNoLayoutReplacesIt() {
        addPageAndBlogLayouts()
        addBlogDefaultLayout()
        myFixture.configureByText(
            "FirstPostPage.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            @NoLayout
            @Composable
            fun First<caret>PostPage() {
            }
            """.trimIndent()
        )

        val context = ActionContext.from(myFixture.editor, myFixture.file)
        val chooser = ChangeLayoutIntention().perform(context) as ModChooseAction
        val choices = chooser.choicesByName(context)

        // The page opted out of its default layout, so choosing it is offered too
        assertThat(choices.keys)
            .containsExactly(".components.layouts.BlogLayout", ".components.layouts.PageLayout")
            .inOrder()
        choices.getValue(".components.layouts.PageLayout").performAndExecute(context)

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

    fun testChoosingDefaultLayoutForPageWithNoLayoutJustRemovesIt() {
        addPageAndBlogLayouts()
        addBlogDefaultLayout()
        myFixture.configureByText(
            "FirstPostPage.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            @NoLayout
            @Composable
            fun First<caret>PostPage() {
            }
            """.trimIndent()
        )

        val context = ActionContext.from(myFixture.editor, myFixture.file)
        val chooser = ChangeLayoutIntention().perform(context) as ModChooseAction
        val choices = chooser.choicesByName(context)
        choices.getValue(".components.layouts.BlogLayout").performAndExecute(context)

        // The page uses its default layout again, which doesn't need a `@Layout` annotation
        myFixture.checkResult(
            // language=kotlin
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun FirstPostPage() {
            }
            """.trimIndent()
        )
    }

    fun testChoosingLayoutForPageUsingDefaultLayoutAddsAnnotation() {
        addPageAndBlogLayouts()
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
        val choices = chooser.choicesByName(context)

        // The page already uses `BlogLayout` by default, so it isn't offered
        assertThat(choices.keys).containsExactly(".components.layouts.PageLayout")
        choices.getValue(".components.layouts.PageLayout").performAndExecute(context)

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

    fun testNotAvailableOnPathToOnlyLayout() {
        addPageLayout()
        assertIntentionNotAvailable(
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

    fun testNotAvailableOnPageUsingOnlyLayoutByDefault() {
        addPageLayout()
        myFixture.addFileToProject(
            "pages/DefaultLayout.kt",
            // language=kotlin
            """
            @file:Layout(".components.layouts.PageLayout")

            package $TEST_GROUP.pages

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )

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
}
