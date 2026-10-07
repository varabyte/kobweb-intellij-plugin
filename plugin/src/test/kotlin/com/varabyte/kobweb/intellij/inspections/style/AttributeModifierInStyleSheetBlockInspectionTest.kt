package com.varabyte.kobweb.intellij.inspections.style

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase

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
            """.trimIndent()
        )
    }
}
