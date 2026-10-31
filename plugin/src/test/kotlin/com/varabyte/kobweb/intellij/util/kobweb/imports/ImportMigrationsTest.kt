@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.util.kobweb.imports

import com.varabyte.truthish.assertThat
import com.varabyte.truthish.assertThrows
import org.jetbrains.kotlin.name.FqName
import org.junit.Test

class ImportMigrationsTest {
    private fun ImportMigrations.targetsOf(from: String) = findMigrationTargets(FqName(from)).map { it.asString() }

    @Test
    fun parseReadsEveryNameOnALine() {
        val migrations = ImportMigrations.parse(
            """
            # A comment
            a.old  a.current  fn1 fn2  # A trailing comment

            a.old${'\t'}a.current${'\t'}fn3
            """.trimIndent()
        )

        listOf("fn1", "fn2", "fn3").forEach {
            assertThat(migrations.targetsOf("a.old.$it")).containsExactly("a.current.$it")
        }
    }

    @Test
    fun parseReadsIndentedLinesAsContinuations() {
        val migrations = ImportMigrations.parse(
            """
            a.old  a.current  fn1
              fn2
            ${'\t'}fn3
              # Comments don't end an entry
              fn4

            b.old  b.current
              fn5
            """.trimIndent()
        )

        listOf("fn1", "fn2", "fn3", "fn4").forEach {
            assertThat(migrations.targetsOf("a.old.$it")).containsExactly("a.current.$it")
        }
        assertThat(migrations.targetsOf("b.old.fn5")).containsExactly("b.current.fn5")
    }

    @Test
    fun parseRejectsMisplacedContinuations() {
        // Nothing to continue
        assertThrows<IllegalArgumentException> { ImportMigrations.parse("  fn") }
        // Blank lines end an entry
        assertThrows<IllegalArgumentException> { ImportMigrations.parse("a.old  a.current  fn1\n\n  fn2") }
    }

    @Test
    fun parseRejectsInvalidLines() {
        listOf(
            "a.old",
            "a.old  a.current",
            "a.old  a.current\n  # No names, just a comment",
            "a.old  a..current  fn",
            "a.old  a.current  1fn",
            "a.old  a.current  other.fn",
            "a.same  a.same  fn",
            "a.old  a.current  fn\n  fn",
        ).forEach { line ->
            assertThrows<IllegalArgumentException> { ImportMigrations.parse("# Valid\na.ok  a.fine  fn\n\n$line") }
        }
    }

    @Test
    fun parseRejectsNamesMovedTwice() {
        val e = assertThrows<IllegalArgumentException> {
            ImportMigrations.parse(
                """
                a.old  a.first  fn
                a.old  a.second  other fn
                """.trimIndent()
            )
        }
        assertThat(e.message!!).startsWith("Line 2 ")
        assertThat(e.message!!).endsWith("was already moved on line 1")
    }

    @Test
    fun findMigrationTargetsFollowsTheChainNewestFirst() {
        val migrations = ImportMigrations.parse(
            """
            # Listed out of order on purpose, as only the links matter
            a.legacy   a.modern  fn
            a.ancient  a.old     fn
            a.old      a.legacy  fn
            """.trimIndent()
        )

        assertThat(migrations.targetsOf("a.ancient.fn"))
            .containsExactly("a.modern.fn", "a.legacy.fn", "a.old.fn")
            .inOrder()
        // Never migrate backwards
        assertThat(migrations.targetsOf("a.legacy.fn")).containsExactly("a.modern.fn")
        assertThat(migrations.targetsOf("a.modern.fn")).isEmpty()
        // Names that never moved
        assertThat(migrations.targetsOf("a.ancient.other")).isEmpty()
    }

    @Test
    fun findMigrationTargetsStopsAtCycles() {
        val migrations = ImportMigrations.parse(
            """
            a.one  a.two  fn
            a.two  a.one  fn
            """.trimIndent()
        )

        assertThat(migrations.targetsOf("a.one.fn")).containsExactly("a.two.fn")
    }
}
