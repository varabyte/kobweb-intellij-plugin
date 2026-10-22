package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.route.DeletePackageMappingFileQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.RemovePackageMappingQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.RemoveRouteOverrideQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.ReplaceRouteValueQuickFix
import com.varabyte.truthish.assertThat

class RedundantRouteInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = RedundantRouteInspection()

    private fun findQuickFixNames(): List<String> = myFixture.getAllQuickFixes().map(IntentionAction::getText)

    fun testRouteOverrideMatchingDefaultIsFlagged() {
        doTest(
            fileName = "Post.kt",
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page(<weak_warning descr="Kobweb would generate the same route (\"/blog/post\") without this override, so it's redundant.">"post"</weak_warning>)
            @Composable
            fun PostPage() {
            }
            """.trimIndent(),
            thenFixWith<RemoveRouteOverrideQuickFix>(
                """
                package $TEST_GROUP.pages.blog

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page

                @Page
                @Composable
                fun PostPage() {
                }
                """.trimIndent()
            ),
        )
    }

    fun testOtherRedundantRouteOverridesAreFlagged() {
        doTest(
            fileName = "Post.kt",
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page(<weak_warning>"/blog/post"</weak_warning>)
            @Composable
            fun AbsolutePostPage() {
            }

            @Page(<weak_warning>"/blog/"</weak_warning>)
            @Composable
            fun TrailingSlashPostPage() {
            }

            // Kobweb treats blank overrides the same as no override
            @Page(<weak_warning>""</weak_warning>)
            @Composable
            fun BlankPostPage() {
            }
            """.trimIndent(),
        )
    }

    fun testIndexRouteOverrideInIndexFileIsFlagged() {
        doTest(
            fileName = "Index.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page(<weak_warning>"index"</weak_warning>)
            @Composable
            fun HomePage() {
            }
            """.trimIndent(),
        )
    }

    fun testDynamicSegmentNamedAfterFileCanBeShortened() {
        doTest(
            fileName = "Post.kt",
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page(<weak_warning descr="Can be simplified to \"{}\", which Kobweb expands to the same route.">"{post}"</weak_warning>)
            @Composable
            fun PostPage() {
            }
            """.trimIndent(),
            thenFixWith<ReplaceRouteValueQuickFix>(
                """
                package $TEST_GROUP.pages.blog

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.core.Page

                @Page("{}")
                @Composable
                fun PostPage() {
                }
                """.trimIndent()
            ),
        )
    }

    fun testRouteOverridesThatChangeTheRouteAreNotFlagged() {
        doTest(
            fileName = "Post.kt",
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("article")
            @Composable
            fun ArticlePage() {
            }

            @Page("{}")
            @Composable
            fun DynamicPostPage() {
            }

            @Page("{id}")
            @Composable
            fun PostByIdPage() {
            }

            // Routes are case-sensitive, so this is a different route from "/blog/post"
            @Page("Post")
            @Composable
            fun UppercasePostPage() {
            }
            """.trimIndent(),
        )
    }

    fun testRedundantPackageMappingInItsOwnFileCanBeDeleted() {
        doTest(
            fileName = "PackageMapping.kt",
            """
            <weak_warning descr="Kobweb would use this name for the package anyway, so this package mapping is redundant.">@file:PackageMapping("our-team")</weak_warning>

            package $TEST_GROUP.pages.ourTeam

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
        )

        assertThat(findQuickFixNames()).containsAllIn(
            DeletePackageMappingFileQuickFix().familyName,
            RemovePackageMappingQuickFix().familyName,
        )

        val file = myFixture.file.virtualFile
        val deleteFix = myFixture.getAllQuickFixes()
            .single { QuickFixWrapper.unwrap(it) is DeletePackageMappingFileQuickFix }
        myFixture.launchAction(deleteFix)
        assertThat(file.isValid).isFalse()
    }

    fun testRedundantPackageMappingInSharedFileIsRemoved() {
        doTest(
            fileName = "PackageMapping.kt",
            """
            <weak_warning>@file:PackageMapping("our-team")</weak_warning>
            @file:Suppress("unused")

            package $TEST_GROUP.pages.ourTeam

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
            thenFixWith<RemovePackageMappingQuickFix>(
                """
                @file:Suppress("unused")

                package $TEST_GROUP.pages.ourTeam
                """.trimIndent()
            ),
        )
        // The file has other content, so we shouldn't offer to delete it
        assertThat(findQuickFixNames()).doesNotContain(DeletePackageMappingFileQuickFix().familyName)
    }

    fun testDynamicPackageMappingNamedAfterPackageCanBeShortened() {
        doTest(
            fileName = "PackageMapping.kt",
            """
            @file:PackageMapping(<weak_warning>"{user}"</weak_warning>)

            package $TEST_GROUP.pages.user

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
            thenFixWith<ReplaceRouteValueQuickFix>(
                """
                @file:PackageMapping("{}")

                package $TEST_GROUP.pages.user

                import com.varabyte.kobweb.core.PackageMapping
                """.trimIndent()
            ),
        )
    }

    fun testPackageMappingsThatChangeTheRouteAreNotFlagged() {
        doTest(
            fileName = "PackageMapping.kt",
            """
            @file:PackageMapping("members")

            package $TEST_GROUP.pages.user

            import com.varabyte.kobweb.core.PackageMapping
            """.trimIndent(),
        )
    }
}
