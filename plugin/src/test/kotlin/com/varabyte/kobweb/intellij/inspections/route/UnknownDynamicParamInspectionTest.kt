@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModChooseAction
import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.route.ChooseDynamicParamQuickFix
import com.varabyte.kobweb.intellij.test.util.choicesByName
import com.varabyte.kobweb.intellij.test.util.performAndExecute
import com.varabyte.truthish.assertThat

class UnknownDynamicParamInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = UnknownDynamicParamInspection()

    // TODO: Test `ctx.req.dynamicParams` in API endpoints too, once these tests use Kobweb 0.25.2+ (which added it)

    fun testUnknownDynamicParamsAreFlagged() {
        doTest(
            "Post.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage(ctx: PageContext) {
                ctx.route.dynamicParams["user"]
                ctx.route.dynamicParams["post"]
                ctx.route.dynamicParams["<warning descr="This route (\"/users/{user}/posts/{post}\") has no dynamic segment named \"pots\".">pots</warning>"]
                ctx.route.dynamicParams.getValue("<error descr="This route (\"/users/{user}/posts/{post}\") has no dynamic segment named \"id\".">id</error>")
                ctx.route.dynamicParams.get("<warning>id</warning>")
                ctx.route.dynamicParams.getOrElse("<warning>id</warning>") { "" }
                ctx.route.dynamicParams.containsKey("<warning>id</warning>")
            }
            """.trimIndent(),
        )
    }

    /**
     * Run [ChooseDynamicParamQuickFix], checking the names it offers and choosing one of them.
     *
     * Note: Normally, the user would choose from a popup, but tests can't show one, so we choose directly.
     */
    private fun ChooseDynamicParamQuickFix.chooseName(
        problem: ProblemDescriptor,
        expectedChoices: List<String>,
        choice: String,
    ) {
        val context = ActionContext.from(myFixture.editor, myFixture.file)
        val chooser = perform(project, problem) as ModChooseAction
        val choices = chooser.choicesByName(context)

        assertThat(choices.keys).containsExactly(expectedChoices).inOrder()
        choices.getValue(choice).performAndExecute(context)
    }

    fun testUnknownDynamicParamCanBeFixedByChoosingClosestName() {
        doTest(
            "Post.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage(ctx: PageContext) {
                ctx.route.dynamicParams["<warning>pots</warning>"]
            }
            """.trimIndent(),
            thenFixWith<ChooseDynamicParamQuickFix>({ problem ->
                // The closest match is listed first
                chooseName(problem, expectedChoices = listOf("post", "user"), choice = "post")
            },
                """
                package $TEST_GROUP.pages.users

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page
                import com.varabyte.kobweb.core.PageContext

                @Page("{user}/posts/{post}")
                @Composable
                fun PostPage(ctx: PageContext) {
                    ctx.route.dynamicParams.getValue("post")
                }
                """.trimIndent()
            )
        )
    }

    fun testChoosingNameReplacesFallbackWithGetValue() {
        doTest(
            "Post.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage(ctx: PageContext) {
                val post: String = ctx.route.dynamicParams.getOrElse("<warning>pots</warning>") { "" }
            }
            """.trimIndent(),
            thenFixWith<ChooseDynamicParamQuickFix>({ problem ->
                chooseName(problem, expectedChoices = listOf("post", "user"), choice = "post")
            },
                """
                package $TEST_GROUP.pages.users

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page
                import com.varabyte.kobweb.core.PageContext

                @Page("{user}/posts/{post}")
                @Composable
                fun PostPage(ctx: PageContext) {
                    val post: String = ctx.route.dynamicParams.getValue("post")
                }
                """.trimIndent()
            )
        )
    }

    fun testChoosingNameOnlyChangesKeyOfContainsKey() {
        doTest(
            "Post.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage(ctx: PageContext) {
                ctx.route.dynamicParams.containsKey("<warning>pots</warning>")
            }
            """.trimIndent(),
            thenFixWith<ChooseDynamicParamQuickFix>({ problem ->
                chooseName(problem, expectedChoices = listOf("post", "user"), choice = "post")
            },
                """
                package $TEST_GROUP.pages.users

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page
                import com.varabyte.kobweb.core.PageContext

                @Page("{user}/posts/{post}")
                @Composable
                fun PostPage(ctx: PageContext) {
                    ctx.route.dynamicParams.containsKey("post")
                }
                """.trimIndent()
            )
        )
    }

    fun testInferredSegmentNamesAreKnown() {
        doTest(
            "PostId.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{}")
            @Composable
            fun PostPage(ctx: PageContext) {
                ctx.route.dynamicParams["post-id"]
                ctx.route.dynamicParams["<warning>postId</warning>"]
            }
            """.trimIndent(),
        )
    }

    fun testAnyDynamicParamIsFlaggedInRouteWithoutDynamicSegments() {
        doTest(
            "About.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page
            @Composable
            fun AboutPage(ctx: PageContext) {
                ctx.route.dynamicParams["<warning>user</warning>"]
            }
            """.trimIndent(),
        )

        // There are no names to choose from
        val fixNames = myFixture.getAllQuickFixes().map { fix -> fix.familyName }
        assertThat(fixNames).doesNotContain(ChooseDynamicParamQuickFix(emptyList()).familyName)
    }

    fun testParamsAreNotChecked() {
        doTest(
            "Post.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage(ctx: PageContext) {
                // Could be a query parameter, e.g. "?sort=new"
                ctx.route.params["sort"]
            }
            """.trimIndent(),
        )
    }

    fun testInitRouteIsCheckedAgainstThePageInTheSameFile() {
        doTest(
            "Post.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.init.InitRoute
            import com.varabyte.kobweb.core.init.InitRouteContext

            @InitRoute
            fun initPostPage(ctx: InitRouteContext) {
                ctx.route.dynamicParams["post"]
                ctx.route.dynamicParams["<warning>pots</warning>"]
            }

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage() {
            }
            """.trimIndent(),
        )
    }

    fun testKeysOutsideAPageOrWithUnknownValuesAreNotChecked() {
        doTest(
            "Post.kt",
            $$"""
            package $$TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("{user}/posts/{post}")
            @Composable
            fun PostPage(ctx: PageContext, name: String) {
                ctx.route.dynamicParams["$name"]
                PostTitle(ctx)
            }

            // Might be called by other pages
            @Composable
            fun PostTitle(ctx: PageContext) {
                ctx.route.dynamicParams["title"]
            }
            """.trimIndent(),
        )
    }
}
