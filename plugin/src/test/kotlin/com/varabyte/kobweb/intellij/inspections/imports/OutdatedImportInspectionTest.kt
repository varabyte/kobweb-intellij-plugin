@file:Suppress("FunctionName", "UnusedReceiverParameter", "RedundantExplicitType")

package com.varabyte.kobweb.intellij.inspections.imports

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.imports.MigrateImportQuickFix
import com.varabyte.kobweb.intellij.services.imports.ImportMigrationService
import com.varabyte.kobweb.intellij.util.kobweb.imports.ImportMigrations

class OutdatedImportInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = OutdatedImportInspection()

    override fun setUp() {
        super.setUp()

        ImportMigrationService.instance.useMigrationsForTests(
            ImportMigrations.parse(
                """
                lib.old  lib.current  Greeter greet excited shout

                # lib.missing doesn't exist, so lib.legacy is used instead
                lib.old     lib.legacy   wave
                lib.legacy  lib.missing  wave
                """.trimIndent()
            ),
            testRootDisposable,
        )

        myFixture.addFileToProject(
            "lib/old/Greetings.kt",
            // language=kotlin
            $$"""
            package lib.old

            class Greeter
            fun String.greet() = "Hello, $this"
            val String.excited get() = "$this!"
            fun String.shout() = uppercase()
            fun String.shout(times: Int) = uppercase().repeat(times)
            fun String.wave() = "o/ $this"
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "lib/legacy/Greetings.kt",
            // language=kotlin
            $$"""
            package lib.legacy

            fun String.wave() = "o/ $this"
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "lib/current/Greetings.kt",
            // language=kotlin
            $$"""
            package lib.current

            class Greeter
            fun String.greet() = "Hello, $this"
            val String.excited get() = "$this!"
            // The overload that takes a count wasn't moved
            fun String.shout() = uppercase()
            """.trimIndent()
        )
    }

    fun testMovedImportsAreFlagged() {
        doTest(
            """
            package $TEST_GROUP

            import <warning descr="\"lib.old.Greeter\" has moved to \"lib.current.Greeter\".">lib.old.Greeter</warning>
            import <warning descr="\"lib.old.excited\" has moved to \"lib.current.excited\".">lib.old.excited</warning>
            import <warning descr="\"lib.old.greet\" has moved to \"lib.current.greet\".">lib.old.greet</warning>

            fun main() {
                val greeter: Greeter = Greeter()
                println("World".greet().excited)
            }
            """.trimIndent(),
            thenFixWith<MigrateImportQuickFix>(
                """
                package $TEST_GROUP

                import lib.current.Greeter
                import lib.current.excited
                import lib.current.greet

                fun main() {
                    val greeter: Greeter = Greeter()
                    println("World".greet().excited)
                }
                """.trimIndent()
            ),
        )
    }

    fun testAliasesAreKept() {
        doTest(
            """
            package $TEST_GROUP

            import <warning descr="\"lib.old.greet\" has moved to \"lib.current.greet\".">lib.old.greet</warning> as hello

            fun main() {
                println("World".hello())
            }
            """.trimIndent(),
            thenFixWith<MigrateImportQuickFix>(
                """
                package $TEST_GROUP

                import lib.current.greet as hello

                fun main() {
                    println("World".hello())
                }
                """.trimIndent()
            ),
        )
    }

    fun testExistingNewImportIsNotDuplicated() {
        doTest(
            """
            package $TEST_GROUP

            import lib.current.greet
            import <warning descr="\"lib.old.greet\" has moved to \"lib.current.greet\".">lib.old.greet</warning>

            fun main() {
                println("World")
            }
            """.trimIndent(),
            thenFixWith<MigrateImportQuickFix>(
                """
                package $TEST_GROUP

                import lib.current.greet

                fun main() {
                    println("World")
                }
                """.trimIndent()
            ),
        )
    }

    fun testFallsBackToTheNewestTargetThatExists() {
        doTest(
            """
            package $TEST_GROUP

            import <warning descr="\"lib.old.wave\" has moved to \"lib.legacy.wave\".">lib.old.wave</warning>

            fun main() {
                println("World".wave())
            }
            """.trimIndent(),
            thenFixWith<MigrateImportQuickFix>(
                """
                package $TEST_GROUP

                import lib.legacy.wave

                fun main() {
                    println("World".wave())
                }
                """.trimIndent()
            ),
        )
    }

    fun testImportsWithUsagesThatWouldBreakAreNotFlagged() {
        doTest(
            """
            package $TEST_GROUP

            import lib.old.shout

            fun main() {
                println("World".shout())
                println("World".shout(3))
            }
            """.trimIndent()
        )
    }

    fun testImportsWithOnlyMovedUsagesAreFlagged() {
        doTest(
            """
            package $TEST_GROUP

            import <warning descr="\"lib.old.shout\" has moved to \"lib.current.shout\".">lib.old.shout</warning>

            fun main() {
                println("World".shout())
            }
            """.trimIndent()
        )
    }

    fun testStarImportsAreIgnored() {
        doTest(
            """
            package $TEST_GROUP

            import lib.old.*

            fun main() {
                println("World".greet())
            }
            """.trimIndent()
        )
    }
}
