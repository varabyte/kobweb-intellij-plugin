package com.varabyte.kobweb.intellij.inspections.layout

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModChooseAction
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.command.WriteCommandAction
import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.layout.ChooseLayoutQuickFix
import com.varabyte.kobweb.intellij.quickfixes.layout.ReplaceWithNoLayoutQuickFix
import com.varabyte.truthish.assertThat

class InvalidLayoutPathInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = InvalidLayoutPathInspection()

    override fun setUp() {
        super.setUp()

        myFixture.addFileToProject(
            "PageLayout.kt",
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

            @Composable
            fun NotALayout(content: @Composable () -> Unit) {
                content()
            }
            """.trimIndent()
        )
    }

    fun testValidPathsAreNotFlagged() {
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun Index() {
            }

            @Page
            @Layout("$TEST_GROUP.components.layouts.PageLayout")
            @Composable
            fun About() {
            }
            """.trimIndent()
        )
    }

    fun testPathToMissingLayoutIsFlagged() {
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout("<error descr="No layout method found at `.components.layouts.MissingLayout`.">.components.layouts.MissingLayout</error>")
            @Composable
            fun Index() {
            }
            """.trimIndent()
        )
    }

    fun testRelativePathsAreNotFlaggedWhenGroupIsUnknown() {
        // e.g. IntelliJ restored a Kobweb model saved by an older version of the plugin, which didn't record the group
        registerKobwebProject(group = null)

        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun Index() {
            }

            @Page
            @Layout("<error>$TEST_GROUP.components.layouts.MissingLayout</error>")
            @Composable
            fun About() {
            }
            """.trimIndent()
        )
    }

    fun testPathToFunctionThatIsNotALayoutIsFlagged() {
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout("<error>.components.layouts.NotALayout</error>")
            @Composable
            fun Index() {
            }
            """.trimIndent()
        )
    }

    fun testPathInDefaultLayoutIsChecked() {
        doTest(
            """
            @file:Layout("<error>.components.layouts.MissingLayout</error>")

            package $TEST_GROUP.pages

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )
    }

    fun testDefaultLayoutWithoutPathIsWarned() {
        doTest(
            """
            <warning descr="A `@file:Layout` annotation should specify the path of a layout. Otherwise, Kobweb will ignore it.">@file:Layout</warning>

            package $TEST_GROUP.pages

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )

        doTest(
            """
            <warning>@file:Layout("")</warning>

            package $TEST_GROUP.pages

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )
    }

    fun testPageWithoutPathIsFlagged() {
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            <error descr="A `@Layout` annotation on a page must specify the path of a layout. (To opt a page out of a default layout, use `@NoLayout` instead.)">@Layout</error>
            @Composable
            fun Index() {
            }

            @Page
            <error>@Layout("")</error>
            @Composable
            fun About() {
            }
            """.trimIndent()
        )
    }

    fun testLayoutWithoutPathIsNotFlagged() {
        doTest(
            """
            package $TEST_GROUP.components.layouts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.layout.Layout

            @Layout
            @Composable
            fun DocsLayout(content: @Composable () -> Unit) {
                content()
            }

            @Layout("")
            @Composable
            fun DocsArticleLayout(content: @Composable () -> Unit) {
                content()
            }
            """.trimIndent()
        )
    }

    /**
     * Run [ChooseLayoutQuickFix], checking the layouts it offers and choosing one of them.
     *
     * Note: Normally, the user would choose from a popup, but tests can't show one, so we choose directly.
     */
    private fun ChooseLayoutQuickFix.chooseLayout(
        problem: ProblemDescriptor,
        expectedChoices: List<String>,
        choice: String,
    ) {
        val context = ActionContext.from(myFixture.editor, myFixture.file)
        val chooser = perform(project, problem) as ModChooseAction
        val choices = chooser.actions().associateBy { it.getPresentation(context)!!.name() }

        assertThat(choices.keys).containsExactly(expectedChoices).inOrder()
        WriteCommandAction.runWriteCommandAction(project) {
            ModCommandExecutor.getInstance().executeInBatch(context, choices.getValue(choice).perform(context))
        }
    }

    fun testMisspelledPathCanBeFixedByChoosingClosestLayout() {
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout("<error>.components.layouts.PageLayot</error>")
            @Composable
            fun Index() {
            }
            """.trimIndent(),
            thenFixWith<ChooseLayoutQuickFix>({ problem ->
                // The closest match is listed first
                chooseLayout(
                    problem,
                    expectedChoices = listOf(".components.layouts.PageLayout", ".components.layouts.BlogLayout"),
                    choice = ".components.layouts.PageLayout",
                )
            },
                """
                package $TEST_GROUP.pages

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page
                import com.varabyte.kobweb.core.layout.Layout

                @Page
                @Layout(".components.layouts.PageLayout")
                @Composable
                fun Index() {
                }
                """.trimIndent()
            )
        )
    }

    fun testPageWithoutPathCanBeFixedByChoosingLayout() {
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            <error>@Layout</error>
            @Composable
            fun Index() {
            }
            """.trimIndent(),
            thenFixWith<ChooseLayoutQuickFix>({ problem ->
                // With no path to compare against, layouts are listed alphabetically
                chooseLayout(
                    problem,
                    expectedChoices = listOf(".components.layouts.BlogLayout", ".components.layouts.PageLayout"),
                    choice = ".components.layouts.PageLayout",
                )
            },
                """
                package $TEST_GROUP.pages

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page
                import com.varabyte.kobweb.core.layout.Layout

                @Page
                @Layout(".components.layouts.PageLayout")
                @Composable
                fun Index() {
                }
                """.trimIndent()
            )
        )
    }

    fun testPageWithEmptyPathCanBeFixedByChoosingLayout() {
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            <error>@Layout("")</error>
            @Composable
            fun Index() {
            }
            """.trimIndent(),
            thenFixWith<ChooseLayoutQuickFix>({ problem ->
                chooseLayout(
                    problem,
                    expectedChoices = listOf(".components.layouts.BlogLayout", ".components.layouts.PageLayout"),
                    choice = ".components.layouts.PageLayout",
                )
            },
                """
                package $TEST_GROUP.pages

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page
                import com.varabyte.kobweb.core.layout.Layout

                @Page
                @Layout(".components.layouts.PageLayout")
                @Composable
                fun Index() {
                }
                """.trimIndent()
            )
        )
    }

    fun testDefaultLayoutWithoutPathCanBeFixedByChoosingLayout() {
        doTest(
            """
            <warning>@file:Layout</warning>

            package $TEST_GROUP.pages

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent(),
            thenFixWith<ChooseLayoutQuickFix>({ problem ->
                chooseLayout(
                    problem,
                    expectedChoices = listOf(".components.layouts.BlogLayout", ".components.layouts.PageLayout"),
                    choice = ".components.layouts.PageLayout",
                )
            },
                """
                @file:Layout(".components.layouts.PageLayout")

                package $TEST_GROUP.pages

                import com.varabyte.kobweb.core.layout.Layout
                """.trimIndent()
            )
        )
    }

    fun testPageWithoutPathCanBeFixedWithNoLayout() {
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            <error>@Layout("")</error>
            @Composable
            fun Index() {
            }
            """.trimIndent(),
            thenFixWith<ReplaceWithNoLayoutQuickFix>(
                """
                package $TEST_GROUP.pages

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page
                import com.varabyte.kobweb.core.layout.Layout
                import com.varabyte.kobweb.core.layout.NoLayout

                @Page
                @NoLayout
                @Composable
                fun Index() {
                }
                """.trimIndent()
            )
        )
    }
}
