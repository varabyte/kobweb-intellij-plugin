@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.imports

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.model.AppBlock
import com.varabyte.kobweb.intellij.model.IconsBlock
import com.varabyte.kobweb.intellij.quickfixes.imports.MigrateImportQuickFix

class LegacyIconImportInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = LegacyIconImportInspection()

    private fun addGeneratedFaIcons() {
        // A stand-in for the bindings Kobweb generates for `kobweb { app { icons { fa() } } }`, which (unlike the real
        // ones) only include the house icon
        myFixture.addFileToProject(
            "components/icons/fa/FaIcons.kt",
            """
            package $TEST_GROUP.components.icons.fa

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.compose.ui.Modifier

            @Composable
            fun FaHouse(modifier: Modifier = Modifier) {}
            """.trimIndent()
        )
    }

    fun testLegacyIconImportsAreFlagged() {
        registerKobwebProject(app = AppBlock(icons = IconsBlock(rootPackage = ".components.icons", iconSets = listOf("fa"))))
        addGeneratedFaIcons()

        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import <weak_warning descr="This module generates its own copy of the icons in \"com.varabyte.kobweb.silk.components.icons.fa\", in \"$TEST_GROUP.components.icons.fa\". Import them from there instead.">com.varabyte.kobweb.silk.components.icons.fa.FaHouse</weak_warning>

            @Composable
            fun HomePage() {
                FaHouse()
            }
            """.trimIndent(),
            thenFixWith<MigrateImportQuickFix>(
                """
                package $TEST_GROUP.pages

                import androidx.compose.runtime.Composable
                import $TEST_GROUP.components.icons.fa.FaHouse

                @Composable
                fun HomePage() {
                    FaHouse()
                }
                """.trimIndent()
            ),
        )
    }

    fun testIconsMissingFromGeneratedBindingsAreNotFlagged() {
        registerKobwebProject(app = AppBlock(icons = IconsBlock(rootPackage = ".components.icons", iconSets = listOf("fa"))))
        addGeneratedFaIcons()

        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.icons.fa.FaGithub

            @Composable
            fun HomePage() {
                FaGithub()
            }
            """.trimIndent()
        )
    }

    fun testLegacyIconImportsAreNotFlaggedWithoutGeneratedIcons() {
        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.icons.fa.FaHouse

            @Composable
            fun HomePage() {
                FaHouse()
            }
            """.trimIndent()
        )
    }

    fun testLegacyStarImportsAreFlagged() {
        registerKobwebProject(app = AppBlock(icons = IconsBlock(rootPackage = ".components.icons", iconSets = listOf("fa"))))
        addGeneratedFaIcons()

        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import <weak_warning descr="This module generates its own copy of the icons in \"com.varabyte.kobweb.silk.components.icons.fa\", in \"$TEST_GROUP.components.icons.fa\". Import them from there instead.">com.varabyte.kobweb.silk.components.icons.fa</weak_warning>.*

            @Composable
            fun HomePage() {
                FaHouse()
            }
            """.trimIndent(),
            thenFixWith<MigrateImportQuickFix>(
                """
                package $TEST_GROUP.pages

                import androidx.compose.runtime.Composable
                import $TEST_GROUP.components.icons.fa.*

                @Composable
                fun HomePage() {
                    FaHouse()
                }
                """.trimIndent()
            ),
        )
    }

    fun testLegacyStarImportsAreNotFlaggedIfAnUsedIconIsMissingFromGeneratedBindings() {
        registerKobwebProject(app = AppBlock(icons = IconsBlock(rootPackage = ".components.icons", iconSets = listOf("fa"))))
        addGeneratedFaIcons()

        doTest(
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.icons.fa.*

            @Composable
            fun HomePage() {
                FaHouse()
                FaGithub()
            }
            """.trimIndent()
        )
    }
}
