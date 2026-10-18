package com.varabyte.kobweb.intellij.inspections.worker

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase
import com.varabyte.kobweb.intellij.inspections.modifier.DanglingModifierInspection
import com.varabyte.kobweb.intellij.quickfixes.ReplaceCalleeQuickFix
import com.varabyte.kobweb.intellij.quickfixes.style.WrapModifierInBaseBlockQuickFix
import com.varabyte.kobweb.intellij.test.util.configureByTextAndHighlight

class RememberWorkerInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = RememberWorkerInspection()

    fun testUnusedModifierChainIsFlagged() {
        myFixture.configureByTextAndHighlight(
            "DummyWorker.kt",
            // language=kotlin
            """
            import com.varabyte.kobweb.worker.Attachments
            import com.varabyte.kobweb.worker.Worker
            import com.varabyte.kobweb.worker.WorkerContext

            class DummyWorker : Worker<String, String> {
                override var onOutput: WorkerContext.(String) -> Unit 
                    get() = TODO()
                    set(value) = TODO()
    
                override fun postInput(input: String, attachments: Attachments) = TODO()
                override fun terminate() = TODO()
            }
            """.trimIndent()
        )

        doTest(
            """
            import androidx.compose.runtime.*

            @Composable
            fun SomePage() {
                val worker = <warning>remember</warning> { DummyWorker() }
            }
            """.trimIndent(),
            thenFixWith<ReplaceCalleeQuickFix>(
                """
                import androidx.compose.runtime.*
                import com.varabyte.kobweb.worker.rememberWorker
        
                @Composable
                fun SomePage() {
                    val worker = rememberWorker { DummyWorker() }
                }
                """.trimIndent(),
            )
        )
    }

}
