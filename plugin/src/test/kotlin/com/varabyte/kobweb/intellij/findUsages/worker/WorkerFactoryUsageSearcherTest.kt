package com.varabyte.kobweb.intellij.findUsages.worker

import com.varabyte.kobweb.intellij.test.fixtures.KobwebWorkerTestCase
import com.varabyte.kobweb.intellij.test.util.findUsages
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class WorkerFactoryUsageSearcherTest : KobwebWorkerTestCase() {
    @Language("kotlin")
    private fun produceWorkerFactory(className: String) = """
        package com.example

        import com.varabyte.kobweb.worker.OutputDispatcher
        import com.varabyte.kobweb.worker.WorkerFactory
        import com.varabyte.kobweb.worker.WorkerStrategy
        import com.varabyte.kobweb.serialization.IOSerializer

        internal class $className : WorkerFactory<String, String> {
            override fun createStrategy(postOutput: OutputDispatcher<String>) = WorkerStrategy<String> { input -> postOutput(input) }
            override fun createIOSerializer(): IOSerializer<String, String> = TODO()
        }
    """.trimIndent()

    private fun addWorkerFactory(className: String = "CalculatePiWorkerFactory", path: String = "com/example/Factory.kt"): KtClass {
        val file = myFixture.addFileToProject(path, produceWorkerFactory(className)) as KtFile
        return file.findDescendantOfType<KtClass> { it.name == className }!!
    }

    private fun addCodeWithWorkerCalls() {
        myFixture.addFileToProject(
            "com/example/site/Usage.kt",
            """
            package com.example.site

            import com.example.CalculatePiWorker

            fun useWorker() {
                val worker = CalculatePiWorker { output -> println(output) }
                worker.postInput("hello")
            }
            """.trimIndent()
        )
    }

    fun testFactoryUsagesIncludeWorkerUsages() {
        val factory = addWorkerFactory()
        addCodeWithWorkerCalls()

        assertThat(project.findUsages(factory)).containsExactly(
            "Usage.kt: import com.example.[CalculatePiWorker]",
            "Usage.kt: val worker = [CalculatePiWorker] { output -> println(output) }",
        )
    }

    fun testNoWorkerUsagesIfMultipleFactories() {
        // Kobweb doesn't generate a worker if there are multiple factories, so there's no worker to find usages of
        val factory = addWorkerFactory()
        addWorkerFactory("OtherCalculateWorkerFactory", "com/example/OtherFactory.kt")
        addCodeWithWorkerCalls()

        assertThat(project.findUsages(factory)).isEmpty()
    }
}
