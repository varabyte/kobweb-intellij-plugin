package com.varabyte.kobweb.intellij.inspections.supressors

import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.kobweb.intellij.test.util.configureByTextAndHighlight
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UnusedSymbolInspection

class UnusedInspectionSuppressorApplicationTest : KobwebApplicationTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(UnusedSymbolInspection())
    }

    fun testFunctionsCalledByGeneratedCodeAreNotReportedAsUnused() {
        // Kobweb generates code that calls functions with these annotations
        myFixture.configureByTextAndHighlight(
            "Index.kt",
            // language=kotlin
            """
            package com.example

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.App
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.init.InitKobweb
            import com.varabyte.kobweb.core.init.InitKobwebContext
            import com.varabyte.kobweb.silk.init.InitSilk
            import com.varabyte.kobweb.silk.init.InitSilkContext

            @App
            @Composable
            fun AppEntry(content: @Composable () -> Unit) {
                content()
            }

            @Page
            @Composable
            fun HomePage() {}

            @InitKobweb
            fun initKobweb(ctx: InitKobwebContext) {
                println(ctx)
            }

            @InitSilk
            fun initSilk(ctx: InitSilkContext) {
                println(ctx)
            }
            """.trimIndent()
        )
    }

    // Control test, to ensure that our other test is genuinely suppressing unused warnings
    fun testUnusedCodeIsBeingReported() {
        myFixture.configureByTextAndHighlight(
            "Utils.kt",
            // language=kotlin
            """
            package com.example

            internal fun <warning>helper</warning>() {}
            """.trimIndent()
        )
    }
}
