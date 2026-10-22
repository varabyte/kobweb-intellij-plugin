package com.varabyte.kobweb.intellij.inspections.route

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.route.RemovePackageMappingQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.ReplaceRouteValueQuickFix

class InvalidRouteInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = InvalidRouteInspection()

    fun testValidRoutesAreNotFlagged() {
        doTest(
            fileName = "Posts.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun PostsPage() {
            }

            @Page("{}")
            @Composable
            fun UserPage() {
            }

            @Page("/members/{member}/posts/{...rest?}")
            @Composable
            fun MemberPostsPage() {
            }

            @Page("")
            @Composable
            fun BlankOverridePage() {
            }
            """.trimIndent(),
        )
    }

    fun testMisplacedDynamicSegmentsAreFlagged() {
        doTest(
            fileName = "Posts.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("<warning descr="Inferred (`{}`), catch-all (`{...}`), and optional (`{?}`) dynamic segments are only allowed at the end of a route. Otherwise, Kobweb skips the page.">{}</warning>/posts")
            @Composable
            fun UserPostsPage() {
            }

            @Page("/files/<warning>{...path}</warning>/edit")
            @Composable
            fun EditFilePage() {
            }
            """.trimIndent(),
        )
    }

    fun testMalformedDynamicSegmentsAreFlagged() {
        doTest(
            fileName = "Post.kt",
            """
            package $TEST_GROUP.pages.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("/posts/<warning descr="`{post` isn't a valid dynamic segment. Dynamic segments must be a single name enclosed in braces, like `{name}`.">{post</warning>")
            @Composable
            fun PostPage() {
            }
            """.trimIndent(),
        )
    }

    fun testEmptySegmentsAreFlagged() {
        doTest(
            fileName = "About.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("/company<warning descr="Routes shouldn't contain empty segments.">//</warning>about")
            @Composable
            fun AboutPage() {
            }
            """.trimIndent(),
            thenFixWith<ReplaceRouteValueQuickFix>(
                """
                package $TEST_GROUP.pages

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page

                @Page("/company/about")
                @Composable
                fun AboutPage() {
                }
                """.trimIndent()
            ),
        )
    }

    fun testInvalidCharactersAreFlagged() {
        doTest(
            fileName = "Drafts.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("<warning descr="`?` and `#` can't be part of a route.">posts?draft</warning>")
            @Composable
            fun DraftsPage() {
            }
            """.trimIndent(),
        )
    }

    fun testEscapeSequencesAreDecoded() {
        // "\u003F" is "?", so the problem's position in the string's text is unclear, and the whole value is
        // highlighted instead
        doTest(
            fileName = "Drafts.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("<warning descr="`?` and `#` can't be part of a route.">posts\u003Fdraft</warning>")
            @Composable
            fun DraftsPage() {
            }
            """.trimIndent(),
        )
    }

    fun testEscapeSequencesAreDecodedWhenFixing() {
        // "\u002F" is "/"
        doTest(
            fileName = "About.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("<warning>/company/\u002Fabout</warning>")
            @Composable
            fun AboutPage() {
            }
            """.trimIndent(),
            thenFixWith<ReplaceRouteValueQuickFix>(
                """
                package $TEST_GROUP.pages

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page

                @Page("/company/about")
                @Composable
                fun AboutPage() {
                }
                """.trimIndent()
            ),
        )
    }

    fun testPagesOutsidePagesPackageNeedAnAbsoluteRoute() {
        doTest(
            fileName = "Widget.kt",
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            <warning descr="This page is outside the pages package (`$TEST_GROUP.pages`), so Kobweb will skip it, unless its route override is absolute (i.e. starts with a slash).">@Page</warning>
            @Composable
            fun WidgetPage() {
            }

            // Absolute routes are fine
            @Page("/demos/widget")
            @Composable
            fun WidgetDemoPage() {
            }
            """.trimIndent(),
        )
    }

    fun testRelativeRouteOutsidePagesPackageCanBeMadeAbsolute() {
        doTest(
            fileName = "Widget.kt",
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page(<warning>"widget"</warning>)
            @Composable
            fun WidgetPage() {
            }
            """.trimIndent(),
            thenFixWith<ReplaceRouteValueQuickFix>(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page

                @Page("/widget")
                @Composable
                fun WidgetPage() {
                }
                """.trimIndent()
            ),
        )
    }

    fun testRoutesStartingWithApiAreFlagged() {
        doTest(
            fileName = "Status.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page(<error descr="This page's route (\"/api/status\") starts with \"api\", which Kobweb reserves for API routes, so it will fail the build. Please choose another route.">"/api/status"</error>)
            @Composable
            fun StatusPage() {
            }

            // Only the first segment matters
            @Page("/docs/api")
            @Composable
            fun ApiDocsPage() {
            }
            """.trimIndent(),
        )
    }

    fun testRoutesFromApiPackageAreFlagged() {
        doTest(
            fileName = "Status.kt",
            """
            package $TEST_GROUP.pages.api

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            <error>@Page</error>
            @Composable
            fun StatusPage() {
            }
            """.trimIndent(),
        )
    }

    fun testRouteOverridesThatAreNotLiteralsAreIgnored() {
        doTest(
            fileName = "Widget.kt",
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            const val WIDGET_ROUTE = "{widget"

            @Page(WIDGET_ROUTE)
            @Composable
            fun WidgetPage() {
            }
            """.trimIndent(),
        )
    }

    fun testPackageMappingsCantUseCatchAllOrOptionalSegments() {
        doTest(
            fileName = "PackageMapping.kt",
            """
            @file:PackageMapping("<warning descr="Package mappings can't use catch-all (`{...}`) or optional (`{?}`) dynamic segments, as a package is never at the end of a route.">{...rest}</warning>")

            package $TEST_GROUP.pages.users

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
        )
    }

    fun testPackageMappingsGetSyntaxChecks() {
        doTest(
            fileName = "PackageMapping.kt",
            """
            @file:PackageMapping("our<warning>//</warning>team")

            package $TEST_GROUP.pages.team

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
            thenFixWith<ReplaceRouteValueQuickFix>(
                """
                @file:PackageMapping("our/team")

                package $TEST_GROUP.pages.team

                import com.varabyte.kobweb.core.PackageMapping
                """.trimIndent()
            ),
        )
    }

    fun testBlankPackageMappingsAreErrors() {
        doTest(
            fileName = "PackageMapping.kt",
            """
            @file:PackageMapping(<error descr="Package mappings can't be blank. To change the route of a package's pages, move them to a different package instead.">""</error>)

            package $TEST_GROUP.pages.blog

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
            thenFixWith<RemovePackageMappingQuickFix>(
                """
                package $TEST_GROUP.pages.blog
                """.trimIndent()
            ),
        )
    }

    fun testPackagesMappedMoreThanOnceAreErrors() {
        myFixture.addFileToProject(
            "pages/blog/BlogMapping.kt",
            // language=kotlin
            """
            @file:PackageMapping("articles")

            package $TEST_GROUP.pages.blog

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent()
        )
        // A mapping for another package doesn't count
        myFixture.addFileToProject(
            "pages/blog/drafts/DraftsMapping.kt",
            // language=kotlin
            """
            @file:PackageMapping("unpublished")

            package $TEST_GROUP.pages.blog.drafts

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent()
        )

        doTest(
            fileName = "PackageMapping.kt",
            """
            <error descr="A package can only be mapped once, but `$TEST_GROUP.pages.blog` is also mapped in \"pages/blog/BlogMapping.kt\".">@file:PackageMapping("posts")</error>

            package $TEST_GROUP.pages.blog

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
        )
    }

    fun testPackageMappingsOutsidePagesPackageAreFlagged() {
        doTest(
            fileName = "PackageMapping.kt",
            """
            <warning descr="Kobweb ignores package mappings outside the pages package (`$TEST_GROUP.pages`).">@file:PackageMapping("elsewhere")</warning>

            package $TEST_GROUP.components

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
        )
    }
}
