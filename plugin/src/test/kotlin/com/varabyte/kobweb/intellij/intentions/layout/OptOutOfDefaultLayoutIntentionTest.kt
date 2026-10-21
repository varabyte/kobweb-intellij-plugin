package com.varabyte.kobweb.intellij.intentions.layout

import com.varabyte.kobweb.intellij.intentions.KobwebIntentionTestBase

class OptOutOfDefaultLayoutIntentionTest : KobwebIntentionTestBase() {
    override val intentionFamilyName = OptOutOfDefaultLayoutIntention().familyName

    override fun setUp() {
        super.setUp()

        myFixture.addFileToProject(
            "components/layouts/Layouts.kt",
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

    fun testPageUsingDefaultLayoutCanOptOut() {
        doTest(
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun Raw<caret>PostPage() {
            }
            """.trimIndent(),
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            @NoLayout
            @Composable
            fun RawPostPage() {
            }
            """.trimIndent()
        )
    }

    fun testAvailableFromPageAnnotation() {
        assertIntentionIsAvailable(
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Pa<caret>ge
            @Composable
            fun RawPostPage() {
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableInPageBody() {
        assertIntentionNotAvailable(
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun RawPostPage() {
                println("Hel<caret>lo")
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableForPagesWithoutDefaultLayout() {
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

    fun testNotAvailableForPagesThatChooseTheirOwnLayout() {
        assertIntentionNotAvailable(
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun Raw<caret>PostPage() {
            }
            """.trimIndent()
        )

        assertIntentionNotAvailable(
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            @NoLayout
            @Composable
            fun Raw<caret>PostPage() {
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableForFunctionsThatAreNotPages() {
        assertIntentionNotAvailable(
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable

            @Composable
            fun Post<caret>Widget() {
            }
            """.trimIndent()
        )
    }
}
