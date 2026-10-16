package com.varabyte.kobweb.intellij.inlay

class KobwebModifierInlayHintsProviderTest : KobwebInlayHintsTestBase() {
    override fun produceInlayProvider() = KobwebModifierInlayHintsProvider()

    private fun attrBadge() = badge("attr")
    private fun styleBadge() = badge("style")

    fun testAllInlayHintsAdded() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.*

            val EXAMPLE_MODIFIER = Modifier
                .id("id")${attrBadge()}
                .fillMaxWidth()${styleBadge()}
                .tabIndex(0)${attrBadge()}
                .color(Colors.Magenta)${styleBadge()}
            """.trimIndent(),
            enabledOptions = setOf(
                KobwebModifierInlayHintsProvider.ATTR_OPTION,
                KobwebModifierInlayHintsProvider.STYLE_OPTION,
            )
        )
    }

    fun testAttrInlayHintsAdded() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.*

            val EXAMPLE_MODIFIER = Modifier
                .id("id")${attrBadge()}
                .fillMaxWidth()
                .tabIndex(0)${attrBadge()}
                .color(Colors.Magenta)
            """.trimIndent(),
            enabledOptions = setOf(
                KobwebModifierInlayHintsProvider.ATTR_OPTION,
            )
        )
    }

    fun testStyleInlayHintsAdded() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.*

            val EXAMPLE_MODIFIER = Modifier
                .id("id")
                .fillMaxWidth()${styleBadge()}
                .tabIndex(0)
                .color(Colors.Magenta)${styleBadge()}
            """.trimIndent(),
            enabledOptions = setOf(
                KobwebModifierInlayHintsProvider.STYLE_OPTION,
            )
        )
    }

    fun testNoInlayHintsAdded() {
        doTest(
            """
            import com.varabyte.kobweb.compose.ui.*
            import com.varabyte.kobweb.compose.ui.graphics.Colors
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import org.jetbrains.compose.web.css.*

            val EXAMPLE_MODIFIER = Modifier
                .id("id")
                .fillMaxWidth()
                .tabIndex(0)
                .color(Colors.Magenta)
            """.trimIndent(),
            enabledOptions = emptySet()
        )
    }


}