package com.varabyte.kobweb.intellij.inspections.style

 import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
 import org.intellij.lang.annotations.Language

 class AttributeModifierInStyleSheetBlockInspectionTest : KobwebApplicationTestCase() {
    private fun doTest(@Language("kotlin") code: String) {
        myFixture.enableInspections(AttributeModifierInStyleSheetBlockInspection())
        myFixture.configureByText("AttrModifierInStyle.kt", code)
        myFixture.checkHighlighting(true, false, false)
    }

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
