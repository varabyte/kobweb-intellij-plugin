@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.intentions.icons

import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertWithMessage
import org.intellij.lang.annotations.Language

class FetchIconsIntentionTest : KobwebApplicationTestCase() {
    // Asks the intention directly, since the test fixture refuses to highlight build scripts (which is how
    // `myFixture.availableIntentions` collects intentions)
    private fun isIntentionAvailable(fileName: String, @Language("kotlin") code: String): Boolean {
        myFixture.configureByText(fileName, code)
        return FetchIconsIntention().isAvailable(project, myFixture.editor, myFixture.file)
    }

    private fun assertIntentionIsAvailable(fileName: String, @Language("kotlin") code: String) {
        assertWithMessage("Intention was (unexpectedly) unavailable for code:\n\n$code")
            .that(isIntentionAvailable(fileName, code)).isTrue()
    }

    private fun assertIntentionNotAvailable(fileName: String, @Language("kotlin") code: String) {
        assertWithMessage("Intention was (unexpectedly) available for code:\n\n$code")
            .that(isIntentionAvailable(fileName, code)).isFalse()
    }

    fun testAvailableOnIconSetInApplication() {
        myFixture.addFileToProject("kobweb-store/icons/fa.lock", "spec=+\nversion=1.0.0\n")

        assertIntentionIsAvailable(
            "build.gradle.kts",
            """
            kobweb {
                app {
                    icons {
                        f<caret>a()
                    }
                }
            }
            """.trimIndent()
        )
    }

    fun testAvailableOnIconSetWithVersionInLibrary() {
        myFixture.addFileToProject("kobweb-store/icons/ms.lock", "spec=+\nversion=1.0.0\n")

        assertIntentionIsAvailable(
            "build.gradle.kts",
            """
            kobweb {
                library {
                    icons {
                        ms(version = "4.<caret>0.0")
                    }
                }
            }
            """.trimIndent()
        )
    }

    fun testAvailableOnIconsBlock() {
        assertIntentionIsAvailable(
            "build.gradle.kts",
            """
            kobweb {
                app {
                    ic<caret>ons {
                        lucide()
                        mdi()
                    }
                }
            }
            """.trimIndent()
        )
    }

    fun testAvailableOnIconSetNextToOtherBlocks() {
        myFixture.addFileToProject("kobweb-store/icons/lucide.lock", "spec=+\nversion=1.0.0\n")

        assertIntentionIsAvailable(
            "build.gradle.kts",
            """
            kobweb {
                app {
                    index {
                        description.set("Powered by Kobweb")
                    }
                    icons {
                        ms()
                        luc<caret>ide()
                        mdi {

                        }
                    }
                }
            }
            """.trimIndent()
        )
    }

    fun testAvailableInsideIconSetBlock() {
        myFixture.addFileToProject("kobweb-store/icons/mdi.lock", "spec=+\nversion=1.0.0\n")

        assertIntentionIsAvailable(
            "build.gradle.kts",
            """
            kobweb {
                app {
                    icons {
                        mdi {
                            <caret>
                        }
                    }
                }
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableOnUnfetchedIconSet() {
        // The unfetched icon set inspection offers a quick fix here instead
        assertIntentionNotAvailable(
            "build.gradle.kts",
            """
            kobweb {
                app {
                    icons {
                        f<caret>a()
                    }
                }
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableOutsideIconsBlock() {
        assertIntentionNotAvailable(
            "build.gradle.kts",
            """
            kobweb {
                app {
                    f<caret>a()
                }
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableOnIconsBlockOutsideKobwebBlock() {
        assertIntentionNotAvailable(
            "build.gradle.kts",
            """
            app {
                icons {
                    f<caret>a()
                }
            }
            """.trimIndent()
        )
    }

    fun testNotAvailableOutsideBuildScripts() {
        assertIntentionNotAvailable(
            "Icons.kt",
            """
            fun kobweb(block: () -> Unit) = block()
            fun app(block: () -> Unit) = block()
            fun icons(block: () -> Unit) = block()
            fun fa() = Unit

            fun main() {
                kobweb {
                    app {
                        icons {
                            f<caret>a()
                        }
                    }
                }
            }
            """.trimIndent()
        )
    }
}
