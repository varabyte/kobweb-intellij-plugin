@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.layout

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.layout.RemoveRedundantLayoutQuickFix

class RedundantLayoutInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = RedundantLayoutInspection()

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

    fun testLayoutMatchingDefaultIsFlagged() {
        doTest(
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            <weak_warning descr="This page already uses this layout by default (set in \"pages/blog/DefaultLayout.kt\"), so this annotation is redundant.">@Layout(".components.layouts.BlogLayout")</weak_warning>
            @Composable
            fun FirstPost() {
            }

            // Paths are compared after resolving them
            @Page
            <weak_warning>@Layout("$TEST_GROUP.components.layouts.BlogLayout")</weak_warning>
            @Composable
            fun SecondPost() {
            }
            """.trimIndent()
        )
    }

    fun testLayoutDifferentFromDefaultIsNotFlagged() {
        doTest(
            """
            package $TEST_GROUP.pages.blog

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
    }

    fun testLayoutWithoutDefaultIsNotFlagged() {
        doTest(
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

    fun testLayoutsAreNotFlagged() {
        // Normally, a layout would not be defined inside a pages package, but it technically can be, so we check it
        // here. A layout's own `@Layout` annotation sets its parent layout, which default layouts don't apply to
        doTest(
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.layout.Layout

            @Layout(".components.layouts.BlogLayout")
            @Composable
            fun BlogPostLayout(content: @Composable () -> Unit) {
                content()
            }
            """.trimIndent()
        )
    }

    fun testRedundantLayoutCanBeRemoved() {
        doTest(
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            <weak_warning>@Layout(".components.layouts.BlogLayout")</weak_warning>
            @Composable
            fun FirstPost() {
            }
            """.trimIndent(),
            thenFixWith<RemoveRedundantLayoutQuickFix>(
                """
                package $TEST_GROUP.pages.blog

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page

                @Page
                @Composable
                fun FirstPost() {
                }
                """.trimIndent()
            )
        )
    }

    fun testRemovingLastAnnotationKeepsCodeOnItsOwnLine() {
        doTest(
            """
            package $TEST_GROUP.pages.blog

            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            <weak_warning>@Layout(".components.layouts.BlogLayout")</weak_warning>
            fun FirstPost() {
            }
            """.trimIndent(),
            thenFixWith<RemoveRedundantLayoutQuickFix>(
                """
                package $TEST_GROUP.pages.blog

                import com.varabyte.kobweb.core.Page

                @Page
                fun FirstPost() {
                }
                """.trimIndent()
            )
        )
    }

    fun testLayoutImportIsKeptWhenStillUsed() {
        doTest(
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            <weak_warning>@Layout(".components.layouts.BlogLayout")</weak_warning>
            @Composable
            fun FirstPost() {
            }

            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun About() {
            }
            """.trimIndent(),
            thenFixWith<RemoveRedundantLayoutQuickFix>(
                """
                package $TEST_GROUP.pages.blog

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page
                import com.varabyte.kobweb.core.layout.Layout

                @Page
                @Composable
                fun FirstPost() {
                }

                @Page
                @Layout(".components.layouts.PageLayout")
                @Composable
                fun About() {
                }
                """.trimIndent()
            )
        )
    }
}
