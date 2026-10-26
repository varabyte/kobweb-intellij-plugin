@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.layout

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase

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
            <error descr="Default layouts must be under the pages package (`$TEST_GROUP.pages`).">@file:Layout(".components.layouts.PageLayout")</error>

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
