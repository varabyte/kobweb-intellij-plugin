@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.folding

import com.intellij.openapi.editor.FoldRegion
import com.intellij.testFramework.EditorTestUtil
import com.varabyte.kobweb.intellij.settings.KobwebAppSettingsService
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language

class PercentEncodedStringFoldingBuilderTest : KobwebApplicationTestCase() {
    /**
     * Returns the fold regions of percent-encoded text in this code, as they are when the file is opened.
     */
    private fun findPercentEncodedFoldRegions(@Language("kotlin") code: String): List<FoldRegion> {
        myFixture.configureByText("Test.kt", code)
        EditorTestUtil.buildInitialFoldingsInBackground(myFixture.editor)
        val document = myFixture.editor.document
        return myFixture.editor.foldingModel.allFoldRegions
            .filter { region -> document.getText(region.textRange).startsWith('%') }
    }

    /**
     * Returns the folded regions of percent-encoded text in this code, as pairs of the text and what it's shown as.
     */
    private fun findPercentEncodedFolds(@Language("kotlin") code: String): List<Pair<String, String>> {
        return findPercentEncodedFoldRegions(code)
            .onEach { region -> assertThat(region.isExpanded).isFalse() }
            .map { region -> region.document.getText(region.textRange) to region.placeholderText }
    }

    fun testNonAsciiCharactersInLinksAreFolded() {
        val folds = findPercentEncodedFolds(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/blog/caf%C3%A9")
                Link("/%EB%B8%94%EB%A1%9C%EA%B7%B8/post?tag=%F0%9F%90%88")
            }
            """.trimIndent()
        )

        assertThat(folds).containsExactly(
            "%C3%A9" to "é",
            "%EB%B8%94%EB%A1%9C%EA%B7%B8" to "블로그",
            "%F0%9F%90%88" to "🐈",
        ).inOrder()
    }

    fun testRouteValuesAreFolded() {
        val folds = findPercentEncodedFolds(
            """
            @file:PackageMapping("%EB%B8%94%EB%A1%9C%EA%B7%B8")

            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.PackageMapping

            @Page("caf%C3%A9")
            @Composable
            fun CafePage() {
            }
            """.trimIndent()
        )

        assertThat(folds).containsExactly(
            "%EB%B8%94%EB%A1%9C%EA%B7%B8" to "블로그",
            "%C3%A9" to "é",
        ).inOrder()
    }

    fun testEncodedAsciiCharactersAreNotFolded() {
        val folds = findPercentEncodedFolds(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                // Character folding intentionally excludes encoded versions of normal ASCII characters
                Link("/a%2Fb/caf%C3%A9%20au%20lait/%C3%A9%7B%C3%A9")
            }
            """.trimIndent()
        )

        assertThat(folds).containsExactly(
            "%C3%A9" to "é",
            "%C3%A9" to "é",
            "%C3%A9" to "é",
        )
    }

    fun testInvisibleAndInvalidCharactersAreNotFolded() {
        val folds = findPercentEncodedFolds(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                // A non-breaking space, a zero-width space, and a zero-width joiner on its own
                Link("/a%C2%A0b/a%E2%80%8Bb/a%E2%80%8Db")
                // A Hangul filler, which is a letter that renders as blank
                Link("/a%E3%85%A4b")
                // Bytes that aren't valid UTF-8
                Link("/a%C3b/a%C3%28b/a%A9b")
            }
            """.trimIndent()
        )

        assertThat(folds).isEmpty()
    }

    fun testCharactersNextToInvisibleOnesAreStillFolded() {
        val folds = findPercentEncodedFolds(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                // "é", a non-breaking space, then "éé"
                Link("/caf%C3%A9%C2%A0%C3%A9%C3%A9")
            }
            """.trimIndent()
        )

        assertThat(folds).containsExactly(
            "%C3%A9" to "é",
            "%C3%A9%C3%A9" to "éé",
        ).inOrder()
    }

    fun testCharactersMadeOfSeveralCodePointsAreKeptTogether() {
        val folds = findPercentEncodedFolds(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                // Emoji joined by zero-width joiners
                Link("/%F0%9F%91%A9%E2%80%8D%F0%9F%92%BB/%F0%9F%91%A8%E2%80%8D%F0%9F%91%A9%E2%80%8D%F0%9F%91%A7")
                // A Persian word with a zero-width non-joiner in it
                Link("/%D9%85%DB%8C%E2%80%8C%D8%AE%D9%88%D8%A7%D9%87%D9%85")
            }
            """.trimIndent()
        )

        assertThat(folds).containsExactly(
            "%F0%9F%91%A9%E2%80%8D%F0%9F%92%BB" to "👩‍💻",
            "%F0%9F%91%A8%E2%80%8D%F0%9F%91%A9%E2%80%8D%F0%9F%91%A7" to "👨‍👩‍👧",
            "%D9%85%DB%8C%E2%80%8C%D8%AE%D9%88%D8%A7%D9%87%D9%85" to "می‌خواهم",
        ).inOrder()
    }

    fun testFormatCharactersThatAreVisibleAreFolded() {
        val folds = findPercentEncodedFolds(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                // The Arabic number sign, a format character that Unicode says should be visible
                Link("/a%D8%80b")
            }
            """.trimIndent()
        )

        assertThat(folds).containsExactly("%D8%80" to "\u0600")
    }

    fun testPrivateUseCharactersAreFolded() {
        val folds = findPercentEncodedFolds(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                // Icon fonts draw their icons with these, and other fonts show them as a "missing glyph" box
                Link("/a%EE%80%80b")
            }
            """.trimIndent()
        )

        assertThat(folds).containsExactly("%EE%80%80" to "\uE000")
    }

    fun testStringsThatArentRoutesAreFolded() {
        val folds = findPercentEncodedFolds(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("https://ko.wikipedia.org/wiki/%EB%B8%94")
                println("caf%C3%A9".length)
            }
            """.trimIndent()
        )

        assertThat(folds).containsExactly(
            "%EB%B8%94" to "블",
            "%C3%A9" to "é",
        ).inOrder()
    }

    fun testFoldsCanBeLeftExpandedByDefault() {
        val folding = KobwebAppSettingsService.getInstance().state.folding
        folding.collapsePercentEncodedStrings = false
        try {
            val regions = findPercentEncodedFoldRegions(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.silk.components.navigation.Link

                @Composable
                fun Nav() {
                    Link("/blog/caf%C3%A9")
                }
                """.trimIndent()
            )

            assertThat(regions.map { region -> region.placeholderText }).containsExactly("é")
            assertThat(regions.single().isExpanded).isTrue()
        } finally {
            folding.collapsePercentEncodedStrings = true
        }
    }
}
