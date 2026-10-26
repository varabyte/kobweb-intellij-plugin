@file:Suppress("FunctionName", "LocalVariableName")

package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInsight.intention.PriorityAction
import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.route.RemovePackageMappingQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.ReplaceRouteValueQuickFix
import com.varabyte.truthish.assertThat

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

            @Page("<error descr="Inferred (`{}`), catch-all (`{...}`), and optional (`{?}`) dynamic segments are only allowed at the end of a route.">{}</error>/posts")
            @Composable
            fun UserPostsPage() {
            }

            @Page("/files/<error>{...path}</error>/edit")
            @Composable
            fun EditFilePage() {
            }
            """.trimIndent(),
        )
    }

    fun testStrayCurlyBracesAreFlagged() {
        doTest(
            fileName = "Post.kt",
            """
            package $TEST_GROUP.pages.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("/posts/<error descr="`{post` has curly braces but isn't a dynamic segment. Dynamic segments look like `{name}`, `{name?}`, `{...name}`, or `{...name?}` (or `{}` to infer the name). To use curly braces literally, percent-encode them as `%7B` and `%7D`.">{post</error>")
            @Composable
            fun PostPage() {
            }
            """.trimIndent(),
        )
    }

    fun testStrayCurlyBracesCanBeCompleted() {
        doTest(
            fileName = "Post.kt",
            """
            package $TEST_GROUP.pages.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("/posts/<error>{post</error>/<error>comments}</error>")
            @Composable
            fun PostPage() {
            }
            """.trimIndent(),
        )

        myFixture.launchAction(myFixture.getAllQuickFixes().single { it.text == "Replace with \"/posts/{post}/comments}\"" })
        myFixture.launchAction(myFixture.getAllQuickFixes().single { it.text == "Replace with \"/posts/{post}/{comments}\"" })
        myFixture.checkResult(
            """
            package $TEST_GROUP.pages.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("/posts/{post}/{comments}")
            @Composable
            fun PostPage() {
            }
            """.trimIndent()
        )
    }

    fun testStrayCurlyBracesCanBePercentEncoded() {
        doTest(
            fileName = "Post.kt",
            """
            package $TEST_GROUP.pages.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("/posts/<error>{post</error>/<error>a{b}c</error>")
            @Composable
            fun PostPage() {
            }
            """.trimIndent(),
        )

        val encodeFix = myFixture.getAllQuickFixes().first { it.text == "Replace with \"/posts/%7Bpost/a%7Bb%7Dc\"" }
        // Completing the dynamic segment is the likelier fix, so it's listed first
        assertThat((encodeFix as PriorityAction).priority).isEqualTo(PriorityAction.Priority.LOW)

        myFixture.launchAction(encodeFix)
        myFixture.checkResult(
            """
            package $TEST_GROUP.pages.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("/posts/%7Bpost/a%7Bb%7Dc")
            @Composable
            fun PostPage() {
            }
            """.trimIndent()
        )
    }

    fun testMalformedDynamicSegmentsAreFlagged() {
        doTest(
            fileName = "Files.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("/files/<error descr="`{rest...?}` isn't a valid dynamic segment. Dynamic segments look like `{name}`, `{name?}`, `{...name}`, or `{...name?}` (or `{}` to infer the name). Names can only contain letters, digits, `-`, and `_`.">{rest...?}</error>")
            @Composable
            fun FilesPage() {
            }
            """.trimIndent(),
            thenFixWith<ReplaceRouteValueQuickFix>(
                """
                package $TEST_GROUP.pages

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page

                @Page("/files/{...rest?}")
                @Composable
                fun FilesPage() {
                }
                """.trimIndent()
            ),
        )
    }

    fun testDotSegmentsAreFlagged() {
        doTest(
            fileName = "About.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("/company/<error descr="`..` is a relative path segment, which browsers remove from URLs, so the route can never match.">..</error>/about")
            @Composable
            fun AboutPage() {
            }

            // Only whole segments count
            @Page("/.well-known/security")
            @Composable
            fun SecurityPage() {
            }
            """.trimIndent(),
        )
    }

    fun testRepeatedDynamicSegmentNamesAreFlagged() {
        doTest(
            fileName = "Post.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page(<error descr="This page's route (\"/{id}/posts/{...id}\") uses the dynamic segment name(s) \"id\" more than once, so only one of the values can be read. Please rename one of them.">"/{id}/posts/{...id}"</error>)
            @Composable
            fun PostPage() {
            }

            // Static segments can repeat
            @Page("/a/{a}/a")
            @Composable
            fun RepeatedPage() {
            }
            """.trimIndent(),
        )
    }

    fun testRepeatedInferredDynamicSegmentNamesAreFlagged() {
        // The easiest way to hit this, as the names come from different places
        myFixture.addFileToProject(
            "pages/users/user/PackageMapping.kt",
            // language=kotlin
            """
            @file:PackageMapping("{}")

            package $TEST_GROUP.pages.users.user

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent()
        )

        doTest(
            fileName = "User.kt",
            """
            package $TEST_GROUP.pages.users.user

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page(<error descr="This page's route (\"/users/{user}/{user}\") uses the dynamic segment name(s) \"user\" more than once, so only one of the values can be read. Please rename one of them.">"{}"</error>)
            @Composable
            fun UserPage() {
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

            @Page("/company<error descr="Routes can't contain empty segments.">//</error>about")
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

            @Page("<error descr="`posts?draft` contains characters which can't be part of a URL path as is: `?` (`%3F`). To include them in the URL, write their percent-encoded value instead.">posts?draft</error>")
            @Composable
            fun DraftsPage() {
            }
            """.trimIndent(),
        )
    }

    fun testInvalidCharactersCanBePercentEncoded() {
        doTest(
            fileName = "About.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("/{café}/<error descr="`à propos` contains characters which can't be part of a URL path as is: `à` (`%C3%A0`), a space (`%20`). To include them in the URL, write their percent-encoded value instead.">à propos</error>/<error>a`b</error>/")
            @Composable
            fun AboutPage() {
            }
            """.trimIndent(),
            thenFixWith<ReplaceRouteValueQuickFix>(
                """
                package $TEST_GROUP.pages

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page

                @Page("/{café}/%C3%A0%20propos/a%60b/")
                @Composable
                fun AboutPage() {
                }
                """.trimIndent()
            ),
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

            @Page("<error>posts\u003Fdraft</error>")
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

            @Page("<error>/company/\u002Fabout</error>")
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

            <error descr="Pages must be declared under the pages package (`$TEST_GROUP.pages`), unless their route override is absolute (i.e. starts with a slash).">@Page</error>
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

            @Page(<error>"widget"</error>)
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
            @file:PackageMapping("<error descr="Package mappings can't use catch-all (`{...}`) or optional (`{?}`) dynamic segments, as a package is never at the end of a route.">{...rest}</error>")

            package $TEST_GROUP.pages.users

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
        )

        // Every segment of a mapping is checked, as none of them are at the end of a route
        doTest(
            fileName = "PackageMapping.kt",
            """
            @file:PackageMapping("<warning descr="Package mappings are expected to be a single route segment, without slashes.">a/<error descr="Package mappings can't use catch-all (`{...}`) or optional (`{?}`) dynamic segments, as a package is never at the end of a route.">{...rest}</error></warning>")

            package $TEST_GROUP.pages.users

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
        )
    }

    fun testOnlyTheLastSegmentOfAPackageMappingCanBeInferred() {
        doTest(
            fileName = "PackageMapping.kt",
            """
            @file:PackageMapping("<warning><error descr="Only the last segment of a package mapping can be an inferred dynamic segment (`{}`). Please name the others explicitly.">{}</error>/{}</warning>")

            package $TEST_GROUP.pages.users

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
        )
    }

    fun testPackageMappingsGetSyntaxChecks() {
        doTest(
            fileName = "PackageMapping.kt",
            """
            @file:PackageMapping("<warning descr="Package mappings are expected to be a single route segment, without slashes.">our<error descr="Routes can't contain empty segments.">//</error>team</warning>")

            package $TEST_GROUP.pages.team

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
            thenFixWith<ReplaceRouteValueQuickFix>(
                """
                @file:PackageMapping("<warning descr="Package mappings are expected to be a single route segment, without slashes.">our/team</warning>")

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
            <error descr="Package mappings must be declared under the pages package (`$TEST_GROUP.pages`).">@file:PackageMapping("elsewhere")</error>

            package $TEST_GROUP.components

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
        )
    }
}
