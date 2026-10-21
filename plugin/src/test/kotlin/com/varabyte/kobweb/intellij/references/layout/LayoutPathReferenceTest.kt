package com.varabyte.kobweb.intellij.references.layout

import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinMemberInplaceRenameHandler
import org.jetbrains.kotlin.psi.KtNamedFunction

class LayoutPathReferenceTest : KobwebApplicationTestCase() {
    private fun addLayout(pkg: String, name: String) {
        @Language("kotlin")
        val code = """
            package $pkg

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.layout.Layout

            @Layout
            @Composable
            fun $name(content: @Composable () -> Unit) {
                content()
            }
        """.trimIndent()

        myFixture.addFileToProject("$name.kt", code)
    }

    @Language("kotlin")
    private fun producePageUsingLayout(layoutPath: String) = """
        package $TEST_GROUP.pages

        import androidx.compose.runtime.Composable
        import com.varabyte.kobweb.core.Page
        import com.varabyte.kobweb.core.layout.Layout

        @Page
        @Layout("$layoutPath")
        @Composable
        fun Index() {
        }
    """.trimIndent()

    private fun addPageUsingLayout(layoutPath: String) {
        myFixture.configureByText("Index.kt", producePageUsingLayout(layoutPath))
    }

    fun testCompletingLayoutNameInsertsRelativePath() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")
        addPageUsingLayout("PageLa<caret>")

        myFixture.completeBasic()

        myFixture.checkResult(producePageUsingLayout(".components.layouts.PageLayout<caret>"))
    }

    fun testCompletingPartiallyTypedPathReplacesWholePath() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")
        addPageUsingLayout(".components.layouts.PageLa<caret>")

        myFixture.completeBasic()

        myFixture.checkResult(producePageUsingLayout(".components.layouts.PageLayout<caret>"))
    }

    fun testCompletingPathTypedFromItsStart() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")
        addPageUsingLayout(".components.lay<caret>")

        myFixture.completeBasic()

        myFixture.checkResult(producePageUsingLayout(".components.layouts.PageLayout<caret>"))
    }

    fun testCompletingLayoutOutsideGroupInsertsFullyQualifiedPath() {
        addLayout("org.other.layouts", "OtherLayout")
        addPageUsingLayout("OtherLa<caret>")

        myFixture.completeBasic()

        myFixture.checkResult(producePageUsingLayout("org.other.layouts.OtherLayout<caret>"))
    }

    fun testCompletionListsAllLayouts() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")
        addLayout("$TEST_GROUP.components.layouts", "BlogLayout")
        addLayout("org.other.layouts", "OtherLayout")
        addPageUsingLayout("<caret>")

        myFixture.completeBasic()

        assertThat(myFixture.lookupElementStrings!!).containsExactly(
            ".components.layouts.PageLayout",
            ".components.layouts.BlogLayout",
            "org.other.layouts.OtherLayout",
        )
    }

    fun testCompletionExcludesPagesAndSelf() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")
        addLayout("$TEST_GROUP.components.layouts", "BlogLayout")
        // A page with a layout annotation is not itself a layout
        myFixture.addFileToProject(
            "About.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun About() {
            }
            """.trimIndent()
        )

        @Language("kotlin")
        val blogPostLayout = """
            package $TEST_GROUP.components.layouts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.layout.Layout

            @Layout("<caret>")
            @Composable
            fun BlogPostLayout(content: @Composable () -> Unit) {
                content()
            }
        """.trimIndent()
        myFixture.configureByText("BlogPostLayout.kt", blogPostLayout)

        myFixture.completeBasic()

        assertThat(myFixture.lookupElementStrings!!).containsExactly(
            ".components.layouts.PageLayout",
            ".components.layouts.BlogLayout",
        )
    }

    fun testCompletionInDefaultLayoutAnnotation() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")

        myFixture.configureByText(
            "Layout.kt",
            // language=kotlin
            """
            @file:Layout("PageLa<caret>")

            package $TEST_GROUP.pages

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )

        myFixture.completeBasic()

        myFixture.checkResult(
            // language=kotlin
            """
            @file:Layout(".components.layouts.PageLayout<caret>")

            package $TEST_GROUP.pages

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )
    }

    fun testNoCompletionForUnrelatedAnnotations() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")

        @Language("kotlin")
        val code = """
            package $TEST_GROUP.pages

            annotation class Layout(val value: String)

            @Layout("<caret>")
            fun Unrelated() {}
        """.trimIndent()
        myFixture.configureByText("Unrelated.kt", code)

        myFixture.completeBasic()

        assertThat(myFixture.lookupElementStrings.orEmpty()).doesNotContain(".components.layouts.PageLayout")
    }

    fun testRelativePathResolvesToLayout() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")
        addPageUsingLayout(".components.layouts.Page<caret>Layout")

        val resolved = myFixture.getReferenceAtCaretPositionWithAssertion().resolve()

        assertThat((resolved as KtNamedFunction).fqName!!.asString()).isEqualTo("$TEST_GROUP.components.layouts.PageLayout")
    }

    fun testFullyQualifiedPathResolvesToLayout() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")
        addPageUsingLayout("$TEST_GROUP.components.layouts.Page<caret>Layout")

        val resolved = myFixture.getReferenceAtCaretPositionWithAssertion().resolve()

        assertThat((resolved as KtNamedFunction).fqName!!.asString()).isEqualTo("$TEST_GROUP.components.layouts.PageLayout")
    }

    fun testInvalidPathDoesNotResolve() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")
        addPageUsingLayout(".components.layouts.Missing<caret>Layout")

        assertThat(myFixture.getReferenceAtCaretPositionWithAssertion().resolve()).isNull()
    }

    fun testRenamingLayoutUpdatesRelativePath() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")
        addPageUsingLayout(".components.layouts.Page<caret>Layout")

        myFixture.renameElementAtCaret("SiteLayout")

        myFixture.checkResult(producePageUsingLayout(".components.layouts.SiteLayout"))
    }

    fun testRenamingLayoutInPlaceFromItsPathUpdatesOnlyItsName() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")
        addPageUsingLayout(".components.layouts.Page<caret>Layout")

        // i.e. pressing Shift+F6 with the caret inside the path, which renames in place (editing the text under the
        // caret live)
        CodeInsightTestUtil.doInlineRename(
            KotlinMemberInplaceRenameHandler(),
            "SiteLayout",
            myFixture.editor,
            myFixture.elementAtCaret,
        )

        myFixture.checkResult(producePageUsingLayout(".components.layouts.SiteLayout"))
    }

    fun testRenamingLayoutInPlaceUpdatesOnlyItsName() {
        // In-place renaming (i.e. what Shift+F6 does in the editor when possible) edits the references in the current
        // file live, so declare the layout and a page that uses it in the same file
        myFixture.configureByText(
            "Layouts.kt",
            // language=kotlin
            """
            package $TEST_GROUP.components.layouts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Layout
            @Composable
            fun Page<caret>Layout(content: @Composable () -> Unit) {
                content()
            }

            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun HomePage() {
            }
            """.trimIndent()
        )

        CodeInsightTestUtil.doInlineRename(KotlinMemberInplaceRenameHandler(), "SiteLayout", myFixture)

        myFixture.checkResult(
            // language=kotlin
            """
            package $TEST_GROUP.components.layouts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Layout
            @Composable
            fun SiteLayout(content: @Composable () -> Unit) {
                content()
            }

            @Page
            @Layout(".components.layouts.SiteLayout")
            @Composable
            fun HomePage() {
            }
            """.trimIndent()
        )
    }

    fun testRenamingLayoutUpdatesFullyQualifiedPath() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")
        addPageUsingLayout("$TEST_GROUP.components.layouts.Page<caret>Layout")

        myFixture.renameElementAtCaret("SiteLayout")

        myFixture.checkResult(producePageUsingLayout("$TEST_GROUP.components.layouts.SiteLayout"))
    }

    fun testRenamingLayoutUpdatesDefaultLayoutPath() {
        addLayout("$TEST_GROUP.components.layouts", "PageLayout")
        myFixture.configureByText(
            "DefaultLayout.kt",
            // language=kotlin
            """
            @file:Layout(".components.layouts.Page<caret>Layout")

            package $TEST_GROUP.pages

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )

        myFixture.renameElementAtCaret("SiteLayout")

        myFixture.checkResult(
            // language=kotlin
            """
            @file:Layout(".components.layouts.SiteLayout")

            package $TEST_GROUP.pages

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )
    }
}
