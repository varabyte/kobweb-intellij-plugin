@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.layout

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.layout.RemoveDefaultLayoutQuickFix

class InvalidDefaultLayoutInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = InvalidDefaultLayoutInspection()

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

    fun testDefaultLayoutsInPagesPackageAreNotFlagged() {
        doTest(
            """
            @file:Layout(".components.layouts.PageLayout")

            package $TEST_GROUP.pages.blog

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )
    }

    fun testDefaultLayoutsOutsidePagesPackageAreFlagged() {
        doTest(
            """
            <error descr="Default layouts must be declared under the pages package (`$TEST_GROUP.pages`).">@file:Layout(".components.layouts.PageLayout")</error>

            package $TEST_GROUP.components

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )

        // Only whole package names count, so "pages_old" isn't under "pages"
        doTest(
            """
            <error>@file:Layout(".components.layouts.PageLayout")</error>

            package $TEST_GROUP.pages_old

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )
    }

    fun testPackagesGivenMoreThanOneDefaultLayoutAreFlagged() {
        myFixture.addFileToProject(
            "pages/blog/BlogLayout.kt",
            // language=kotlin
            """
            @file:Layout(".components.layouts.PageLayout")

            package $TEST_GROUP.pages.blog

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )
        // A default layout for another package doesn't count
        myFixture.addFileToProject(
            "pages/blog/drafts/DraftsLayout.kt",
            // language=kotlin
            """
            @file:Layout(".components.layouts.PageLayout")

            package $TEST_GROUP.pages.blog.drafts

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )

        // Even pointing at the same layout counts, as the annotation is redundant and could drift into a real conflict
        doTest(
            """
            <error descr="A package can only be given one default layout, but `$TEST_GROUP.pages.blog` is also given one in \"pages/blog/BlogLayout.kt\".">@file:Layout(".components.layouts.PageLayout")</error>

            package $TEST_GROUP.pages.blog

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent(),
            thenFixWith<RemoveDefaultLayoutQuickFix>(
                """
                package $TEST_GROUP.pages.blog
                """.trimIndent()
            ),
        )
    }

    fun testDefaultLayoutsWithoutPathsAreNotDuplicates() {
        // These are reported by `InvalidLayoutPathInspection` instead, and Kobweb ignores them
        myFixture.addFileToProject(
            "pages/blog/BlogLayout.kt",
            // language=kotlin
            """
            @file:Layout

            package $TEST_GROUP.pages.blog

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )

        doTest(
            """
            @file:Layout(".components.layouts.PageLayout")

            package $TEST_GROUP.pages.blog

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )
        doTest(
            """
            @file:Layout("")

            package $TEST_GROUP.pages.blog

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )
    }

    fun testLayoutsOnFunctionsAreNotFlagged() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.layout.Layout

            @Layout(".components.layouts.PageLayout")
            @Composable
            fun NestedLayout(content: @Composable () -> Unit) {
                content()
            }
            """.trimIndent()
        )
    }
}
