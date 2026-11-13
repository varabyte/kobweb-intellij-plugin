@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.route

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.route.ReplaceWithDynamicParamGetValueQuickFix

class DynamicParamLookupInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = DynamicParamLookupInspection()

    // TODO: Test `ctx.req.dynamicParams` in API endpoints too, once these tests use Kobweb 0.25.2+ (which added it)

    fun testDynamicParamsLookupsOfKnownKeysAreFlagged() {
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
                ctx.route.dynamicParams<weak_warning descr="This route always has a value for \"post\", so getValue can read it without handling a missing value.">["post"]</weak_warning>
                ctx.route.dynamicParams<weak_warning>.get("user")</weak_warning>
                ctx.route.dynamicParams<weak_warning>.getOrElse("post") { "" }</weak_warning>
                ctx.route.dynamicParams.getValue("post")
                ctx.route.dynamicParams.containsKey("post")
            }
            """.trimIndent(),
        )
    }

    fun testParamsLookupsOfKnownKeysAreFlagged() {
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
                ctx.route.params<weak_warning descr="\"post\" is a dynamic segment of this route, so you can use dynamicParams.getValue to safely read it instead.">["post"]</weak_warning>
                ctx.route.params<weak_warning>.get("user")</weak_warning>
                ctx.route.params<weak_warning>.getValue("post")</weak_warning>
                ctx.route.params<weak_warning>.getOrElse("post") { "" }</weak_warning>
                ctx.route.params.containsKey("post")
            }
            """.trimIndent(),
        )
    }

    fun testUnknownKeysAreNotFlagged() {
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
                // Reported by UnknownDynamicParamInspection instead
                ctx.route.dynamicParams["pots"]
                // Might be a query param
                ctx.route.params["sort"]
                ctx.route.queryParams["post"]
            }
            """.trimIndent(),
        )
    }

    fun testOptionalCatchAllSegmentIsFlagged() {
        // Kobweb fills an optional catch-all segment with "" when it matches nothing, so the key is still always there
        doTest(
            "Games.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PageContext

            @Page("games/{...slug?}")
            @Composable
            fun GamesPage(ctx: PageContext) {
                ctx.route.dynamicParams<weak_warning>["slug"]</weak_warning>
            }
            """.trimIndent(),
        )
    }

    fun testIndexedLookupCanBeReplacedWithGetValue() {
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
                val post: String = ctx.route.dynamicParams<weak_warning>["post"]</weak_warning>!!
            }
            """.trimIndent(),
            thenFixWith<ReplaceWithDynamicParamGetValueQuickFix>(
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

    fun testGetCallCanBeReplacedWithGetValue() {
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
                val post: String? = ctx.route.dynamicParams<weak_warning>.get("post")</weak_warning>
            }
            """.trimIndent(),
            thenFixWith<ReplaceWithDynamicParamGetValueQuickFix>(
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

    fun testElvisDefaultIsRemovedWhenReplacingWithGetValue() {
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
                val post: String = ctx.route.dynamicParams<weak_warning>["post"]</weak_warning> ?: ""
            }
            """.trimIndent(),
            thenFixWith<ReplaceWithDynamicParamGetValueQuickFix>(
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

    fun testGetOrElseCanBeReplacedWithGetValue() {
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
                val post: String = ctx.route.dynamicParams<weak_warning>.getOrElse("post") { "" }</weak_warning>
            }
            """.trimIndent(),
            thenFixWith<ReplaceWithDynamicParamGetValueQuickFix>(
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

    fun testParamsLookupCanBeReplacedWithDynamicParamsGetValue() {
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
                val post: String = ctx.route.params<weak_warning>["post"]</weak_warning>!!
            }
            """.trimIndent(),
            thenFixWith<ReplaceWithDynamicParamGetValueQuickFix>(
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

    fun testNullableVarTypeIsKeptWhenReplacingWithGetValue() {
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
                var post: String? = ctx.route.dynamicParams<weak_warning>["post"]</weak_warning>
                post = null
            }
            """.trimIndent(),
            thenFixWith<ReplaceWithDynamicParamGetValueQuickFix>(
                """
                package $TEST_GROUP.pages.users

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page
                import com.varabyte.kobweb.core.PageContext

                @Page("{user}/posts/{post}")
                @Composable
                fun PostPage(ctx: PageContext) {
                    var post: String? = ctx.route.dynamicParams.getValue("post")
                    post = null
                }
                """.trimIndent()
            )
        )
    }
}
