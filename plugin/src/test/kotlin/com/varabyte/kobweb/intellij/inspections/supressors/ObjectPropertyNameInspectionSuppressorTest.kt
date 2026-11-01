@file:Suppress("FunctionName", "LocalVariableName")

package com.varabyte.kobweb.intellij.inspections.supressors

import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.kobweb.intellij.test.util.configureByTextAndHighlight
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ObjectPropertyNameInspection

class ObjectPropertyNameInspectionSuppressorTest : KobwebApplicationTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ObjectPropertyNameInspection())
    }

    fun testPrivateStylesCanStartWithAnUnderscore() {
        // Kobweb recommends a leading underscore for private styles, which it otherwise expects to be public
        myFixture.configureByTextAndHighlight(
            "Styles.kt",
            // language=kotlin
            """
            package com.example

            import com.varabyte.kobweb.silk.components.forms.ButtonStyle
            import com.varabyte.kobweb.silk.style.CssStyle
            import com.varabyte.kobweb.silk.style.addVariant
            import com.varabyte.kobweb.silk.style.animation.Keyframes

            private val _MyStyle = CssStyle { }
            private val _MyButtonVariant = ButtonStyle.addVariant { }
            private val _MyKeyframes = Keyframes { }
            """.trimIndent()
        )
    }

    fun testOtherLeadingUnderscoresAreStillReported() {
        myFixture.configureByTextAndHighlight(
            "Styles.kt",
            // language=kotlin
            """
            package com.example

            import com.varabyte.kobweb.silk.style.CssStyle

            // Public styles are registered by Kobweb, so there's no reason for them to start with an underscore
            val <weak_warning descr="Object or top-level property name '_PublicStyle' should not start with an underscore">_PublicStyle</weak_warning> = CssStyle { }
            private val <weak_warning descr="Object or top-level property name '_notAStyle' should not start with an underscore">_notAStyle</weak_warning> = 5
            """.trimIndent()
        )
    }
}
