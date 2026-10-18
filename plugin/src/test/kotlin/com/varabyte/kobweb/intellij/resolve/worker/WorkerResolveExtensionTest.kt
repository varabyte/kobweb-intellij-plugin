package com.varabyte.kobweb.intellij.resolve.worker

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.varabyte.kobweb.intellij.test.fixtures.KobwebWorkerTestCase
import com.varabyte.kobweb.intellij.test.util.configureByTextAndHighlight
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

class WorkerResolveExtensionTest : KobwebWorkerTestCase() {
    @Language("kotlin")
    private fun echoWorkerFactory(className: String = "EchoWorkerFactory", ioType: String = "String") = """
        package com.example

        import com.varabyte.kobweb.worker.OutputDispatcher
        import com.varabyte.kobweb.worker.WorkerFactory
        import com.varabyte.kobweb.worker.WorkerStrategy
        import com.varabyte.kobweb.serialization.IOSerializer

        internal class $className : WorkerFactory<$ioType, $ioType> {
            override fun createStrategy(postOutput: OutputDispatcher<$ioType>) = WorkerStrategy<$ioType> { input -> postOutput(input) }
            override fun createIOSerializer(): IOSerializer<$ioType, $ioType> = TODO()
        }
    """.trimIndent()

    private fun addFactory(@Language("kotlin") text: String = echoWorkerFactory()): PsiFile {
        return myFixture.addFileToProject("com/example/WorkerFactory.kt", text)
    }

    private var classCheckCount = 0

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun classExists(fqcn: String): Boolean {
        val file = myFixture.addFileToProject("ClassCheck${classCheckCount++}.kt", "") as KtFile
        return allowAnalysisOnEdt {
            analyze(file) { findClass(ClassId.topLevel(FqName(fqcn))) != null }
        }
    }

    fun testWorkerClassResolves() {
        addFactory()

        myFixture.configureByTextAndHighlight(
            "Usage.kt",
            // language=kotlin
            """
            import com.example.EchoWorker

            fun useWorker() {
                val worker = EchoWorker { output: String -> println(output.length); println(attachments) }
                worker.postInput("hello")
                worker.terminate()
            }
            """.trimIndent()
        )
    }

    fun testWorkerClassUsesFqcnOverride() {
        setWorkerFqcn(".custom.Echo")
        addFactory()

        assertThat(classExists("com.example.custom.Echo")).isTrue()
        assertThat(classExists("com.example.EchoWorker")).isFalse()
    }

    fun testFactoryNameCanBeAnythingWithFqcnOverride() {
        setWorkerFqcn("com.example.Echo")
        addFactory(echoWorkerFactory(className = "EchoProvider"))

        assertThat(classExists("com.example.Echo")).isTrue()
    }

    fun testNoWorkerClassForInvalidFactoryName() {
        addFactory(echoWorkerFactory(className = "EchoProvider"))

        assertThat(classExists("com.example.EchoProvider")).isTrue() // Sanity check that the factory itself was added
        assertThat(classExists("com.example.Echo")).isFalse()
        assertThat(classExists("com.example.EchoProviderWorker")).isFalse()
    }

    fun testNoWorkerClassIfMultipleFactories() {
        addFactory()
        myFixture.addFileToProject("com/example/Other.kt", echoWorkerFactory(className = "OtherWorkerFactory"))

        assertThat(classExists("com.example.EchoWorker")).isFalse()
        assertThat(classExists("com.example.OtherWorker")).isFalse()
    }

    fun testKspOutputIsShadowedBySynthesizedClass() {
        addFactory()
        // Simulate stale KSP output, generated when the factory used different input / output types
        myFixture.addFileToProject(
            "build/generated/ksp/js/jsMain/kotlin/com/example/EchoWorker.kt",
            // language=kotlin
            """
            package com.example

            class EchoWorker(override var onOutput: com.varabyte.kobweb.worker.WorkerContext.(output: Int) -> Unit = {}) :
                com.varabyte.kobweb.worker.Worker<Int, Int> {
                override fun postInput(input: Int, attachments: com.varabyte.kobweb.worker.Attachments) {}
                override fun terminate() {}
            }
            """.trimIndent()
        )

        // If the KSP output weren't shadowed, we'd get redeclaration / ambiguity errors here, or Int vs. String errors
        myFixture.configureByTextAndHighlight(
            "Usage.kt",
            // language=kotlin
            """
            import com.example.EchoWorker

            fun useWorker() {
                EchoWorker().postInput("hello")
            }
            """.trimIndent()
        )
    }

    fun testWorkerClassUpdatesWhenFactoryChanges() {
        val factoryFile = addFactory()

        @Language("kotlin")
        val usage = """
            import com.example.EchoWorker

            fun useWorker() {
                EchoWorker().postInput(123)
            }
        """.trimIndent()

        myFixture.configureByText("Usage.kt", usage)
        assertThat(myFixture.doHighlighting(HighlightSeverity.ERROR)).isNotEmpty()

        val document = PsiDocumentManager.getInstance(project).getDocument(factoryFile)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(echoWorkerFactory(ioType = "Int"))
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }

        myFixture.checkHighlighting()
    }
}
