package com.varabyte.kobweb.intellij.inspections.supressors

import com.varabyte.kobweb.intellij.test.fixtures.KobwebWorkerTestCase
import com.varabyte.kobweb.intellij.test.util.configureByTextAndHighlight
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UnusedSymbolInspection

class UnusedInspectionSuppressorWorkerTest : KobwebWorkerTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(UnusedSymbolInspection())
    }

    fun testWorkerFactoryIsNotReportedAsUnused() {
        // CalculatePiWorkerFactory is not reported as unused because a synthetic worker is created for it.
        myFixture.configureByTextAndHighlight(
            "WorkerFactory.kt",
            // language=kotlin
            """
            package com.example

            import com.varabyte.kobweb.worker.OutputDispatcher
            import com.varabyte.kobweb.worker.WorkerFactory
            import com.varabyte.kobweb.worker.WorkerStrategy
            import com.varabyte.kobweb.serialization.IOSerializer

            internal class CalculatePiWorkerFactory : WorkerFactory<String, String> {
                override fun createStrategy(postOutput: OutputDispatcher<String>) = WorkerStrategy<String> { input -> postOutput(input) }
                override fun createIOSerializer(): IOSerializer<String, String> = TODO()
            }
            """.trimIndent()
        )
    }

    fun testMultipleWorkerFactoriesAreReportedAsUnused() {
        // Kobweb refuses to generate a worker when there are multiple factories, so nothing uses either of them
        myFixture.configureByTextAndHighlight(
            "WorkerFactories.kt",
            // language=kotlin
            """
            package com.example

            import com.varabyte.kobweb.worker.OutputDispatcher
            import com.varabyte.kobweb.worker.WorkerFactory
            import com.varabyte.kobweb.worker.WorkerStrategy
            import com.varabyte.kobweb.serialization.IOSerializer

            internal class <warning>CalculatePiWorkerFactory</warning> : WorkerFactory<String, String> {
                override fun createStrategy(postOutput: OutputDispatcher<String>) = WorkerStrategy<String> { input -> postOutput(input) }
                override fun createIOSerializer(): IOSerializer<String, String> = TODO()
            }

            internal class <warning>CalculateEWorkerFactory</warning> : WorkerFactory<String, String> {
                override fun createStrategy(postOutput: OutputDispatcher<String>) = WorkerStrategy<String> { input -> postOutput(input) }
                override fun createIOSerializer(): IOSerializer<String, String> = TODO()
            }
            """.trimIndent()
        )
    }

    // Control test, to ensure that our other tests are genuinely suppressing unused warnings
    fun testUnusedCodeIsBeingReported() {
        myFixture.configureByTextAndHighlight(
            "Helper.kt",
            // language=kotlin
            """
            package com.example

            internal class <warning>Helper</warning>
            """.trimIndent()
        )
    }
}
