@file:Suppress("LocalVariableName")

package com.varabyte.kobweb.intellij.inspections.style

import com.intellij.lang.annotation.HighlightSeverity
import com.varabyte.truthish.assertThat
import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.quickfixes.style.DeleteAttrModifierQuickFix
import com.varabyte.kobweb.intellij.quickfixes.style.MoveAttrModifierToExtraModifierQuickFix
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

class AttributeModifierInStyleSheetBlockInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = AttributeModifierInStyleSheetBlockInspection()

    fun testAttributeModifiersInStylesGetHighlighted() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.*

            val TestStyle = CssStyle {
                base {
                    Modifier
                        .<error>tabIndex(0)</error>
                        .fillMaxWidth()
                        .<error>id("id")</error>
                }
            }
            
            fun notErrorOutsideOfStyle() {
                Modifier
                    .tabIndex(0)
                    .fillMaxWidth()
                    .id("id")
            }
            """.trimIndent(),
        )
    }

    fun testCanApplyMoveToExtraModifierQuickFix() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.*

            val TestStyle = CssStyle {
                base {
                    Modifier
                        .<error>tabIndex(0)</error>
                        .fillMaxWidth()
                        .<error>id("id")</error>
                }
            }
            """.trimIndent(),
            thenFixWith<MoveAttrModifierToExtraModifierQuickFix>(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.*

            val TestStyle = CssStyle(extraModifier = { Modifier.tabIndex(0).id("id") }) {
                base {
                    Modifier
                        .fillMaxWidth()
                }
            }
            """.trimIndent())
        )
    }

    fun testMoveToExtraModifierQuickFixWithEmptyArgumentList() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.*

            val TestStyle = CssStyle() {
                base {
                    Modifier
                        .<error>tabIndex(0)</error>
                        .fillMaxWidth()
                }
            }
            """.trimIndent(),
            thenFixWith<MoveAttrModifierToExtraModifierQuickFix>(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.*

            val TestStyle = CssStyle(extraModifier = { Modifier.tabIndex(0) }) {
                base {
                    Modifier
                        .fillMaxWidth()
                }
            }
            """.trimIndent())
        )
    }

    fun testCanDeleteAttributeModifiersQuickFix() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.*

            val TestStyle = CssStyle {
                base {
                    Modifier
                        .<error>tabIndex(0)</error>
                        .fillMaxWidth()
                        .<error>id("id")</error>
                }
            }
            """.trimIndent(),
            thenFixWith<DeleteAttrModifierQuickFix>(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.*

            val TestStyle = CssStyle {
                base {
                    Modifier
                        .fillMaxWidth()
                }
            }
            """.trimIndent())
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun testMessageShowsSuggestedFixContainingHtmlCharacters() {
        myFixture.enableInspections(produceInspection())
        myFixture.configureByText(
            "KobwebInspectionTest.kt",
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.silk.style.*

            val TestStyle = CssStyle.base {
                Modifier.id("a<b")
            }
            """.trimIndent(),
        )
        val info = allowAnalysisOnEdt { myFixture.doHighlighting(HighlightSeverity.ERROR) }.single()
        assertThat(info.description).contains("""CssStyle.base(extraModifier = { Modifier.id("a<b") })""")
    }
}
