package com.varabyte.kobweb.intellij.inspections.layout

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.layout.RemoveLayoutQuickFix
import com.varabyte.kobweb.intellij.quickfixes.layout.RemoveNoLayoutQuickFix

class ConflictingLayoutAnnotationsInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = ConflictingLayoutAnnotationsInspection()

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
            """.trimIndent()
        )
    }

    fun testPageWithLayoutAndNoLayoutIsFlagged() {
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            <error descr="A page can't be annotated with both `@Layout` and `@NoLayout`. Please remove one of them.">@Layout(".components.layouts.PageLayout")</error>
            <error descr="A page can't be annotated with both `@Layout` and `@NoLayout`. Please remove one of them.">@NoLayout</error>
            @Composable
            fun HomePage() {
            }
            """.trimIndent()
        )
    }

    fun testInvalidLayoutPathStillConflicts() {
        // The path is reported as invalid too (by another inspection), but that shouldn't hide the conflict
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            <error>@Layout(".components.layouts.MissingLayout")</error>
            <error>@NoLayout</error>
            @Composable
            fun HomePage() {
            }
            """.trimIndent()
        )
    }

    fun testLayoutWithoutPathIsNotFlagged() {
        // Kobweb treats a `@Layout` without a path as having no layout, so it agrees with `@NoLayout`
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            @Layout("")
            @NoLayout
            @Composable
            fun HomePage() {
            }
            """.trimIndent()
        )
    }

    fun testNonPagesAreNotFlagged() {
        // Kobweb ignores `@NoLayout` on anything but pages
        doTest(
            """
            package $TEST_GROUP.components.layouts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.layout.Layout
            import com.varabyte.kobweb.core.layout.NoLayout

            @Layout(".components.layouts.PageLayout")
            @NoLayout
            @Composable
            fun BlogLayout(content: @Composable () -> Unit) {
                content()
            }
            """.trimIndent()
        )
    }

    fun testLayoutCanBeRemoved() {
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            <error>@Layout(".components.layouts.PageLayout")</error>
            <error>@NoLayout</error>
            @Composable
            fun HomePage() {
            }
            """.trimIndent(),
            thenFixWith<RemoveLayoutQuickFix>(
                """
                package $TEST_GROUP.pages

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page
                import com.varabyte.kobweb.core.layout.NoLayout

                @Page
                @NoLayout
                @Composable
                fun HomePage() {
                }
                """.trimIndent()
            )
        )
    }

    fun testNoLayoutCanBeRemoved() {
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            <error>@Layout(".components.layouts.PageLayout")</error>
            <error>@NoLayout</error>
            @Composable
            fun HomePage() {
            }
            """.trimIndent(),
            thenFixWith<RemoveNoLayoutQuickFix>(
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
        )
    }
}
