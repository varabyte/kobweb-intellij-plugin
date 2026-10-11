package com.varabyte.kobweb.intellij.util.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.varabyte.kobweb.intellij.test.util.elementUnderCaret
import com.varabyte.truthish.assertThat

class CommentUtilsTest : BasePlatformTestCase() {
    fun testAttachedCommentsFoundAroundProperty_CaretOnVal() {
        myFixture.configureByText(
            "test.kt",
            // language=kotlin
            """
                fun f() {
                    // Overhead comment
                    va<caret>l x = 10 // Trailing comment
                }
            """.trimIndent()
        )

        myFixture.elementUnderCaret.let { it ->
            val comments = it.findAttachedComments().normalizeToStrings()
            assertThat(comments.overhead).hasSize(1)
            assertThat(comments.overhead[0]).isEqualTo("Overhead comment")
            assertThat(comments.trailing).isEqualTo("Trailing comment")
        }
    }

    fun testAttachedCommentsFoundAroundProperty_CaretOnValue() {
        myFixture.configureByText(
            "test.kt",
            // language=kotlin
            """
                fun f() {
                    // Overhead comment
                    val x = 1<caret>0 // Trailing comment
                }
            """.trimIndent()
        )

        myFixture.elementUnderCaret.let { it ->
            val comments = it.findAttachedComments().normalizeToStrings()
            assertThat(comments.overhead).hasSize(1)
            assertThat(comments.overhead[0]).isEqualTo("Overhead comment")
            assertThat(comments.trailing).isEqualTo("Trailing comment")
        }
    }

    fun testMultilineOverheadCommentFound() {
        myFixture.configureByText(
            "test.kt",
            // language=kotlin
            """
                fun f() {
                    // This overhead comment has multiple lines
                    //
                    // Blank lines are allowed
                    va<caret>l x = 10
                }
            """.trimIndent()
        )

        myFixture.elementUnderCaret.let { it ->
            val comments = it.findAttachedComments().normalizeToStrings()
            assertThat(comments.overhead).containsExactly(
                "This overhead comment has multiple lines",
                "",
                "Blank lines are allowed"
            ).inOrder()
            assertThat(comments.trailing).isNull()
        }
    }

    fun testAttachedCommentsFoundDotQualifiedExpr_CaretOnFirst() {
        myFixture.configureByText(
            "test.kt",
            // language=kotlin
            """
                fun f() {
                    // Overhead comment
                    fir<caret>st.second.third.fourth.fifth // Trailing comment
                }
            """.trimIndent()
        )

        myFixture.elementUnderCaret.let { it ->
            val comments = it.findAttachedComments().normalizeToStrings()
            assertThat(comments.overhead).hasSize(1)
            assertThat(comments.overhead[0]).isEqualTo("Overhead comment")
            assertThat(comments.trailing).isEqualTo("Trailing comment")
        }
    }

    fun testAttachedCommentsFoundDotQualifiedExpr_CaretSomewhereInside() {
        myFixture.configureByText(
            "test.kt",
            // language=kotlin
            """
                fun f() {
                    // Overhead comment
                    first.second.third.fo<caret>urth.fifth // Trailing comment
                }
            """.trimIndent()
        )

        myFixture.elementUnderCaret.let { it ->
            val comments = it.findAttachedComments().normalizeToStrings()
            assertThat(comments.overhead).hasSize(1)
            assertThat(comments.overhead[0]).isEqualTo("Overhead comment")
            assertThat(comments.trailing).isEqualTo("Trailing comment")
        }
    }

    fun testAttachedCommentsNormalizesBlockCommentsAsWell() {
        myFixture.configureByText(
            "test.kt",
            // language=kotlin
            """
                fun f() {
                    /*
                        Multi-line
                        overhead comment
                        using block format
                     */
                    va<caret>l x = 10 /* Trailing comment */
                }
            """.trimIndent()
        )

        myFixture.elementUnderCaret.let { it ->
            val comments = it.findAttachedComments().normalizeToStrings()
            assertThat(comments.overhead).containsExactly(
                "Multi-line",
                "overhead comment",
                "using block format"
            ).inOrder()
            assertThat(comments.trailing).isEqualTo("Trailing comment")
        }
    }

}