package com.varabyte.kobweb.intellij.rename.worker

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.varabyte.kobweb.intellij.test.fixtures.KobwebWorkerTestCase
import com.varabyte.truthish.assertThat
import com.varabyte.truthish.assertThrows
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class WorkerFactoryRenameProcessorTest : KobwebWorkerTestCase() {
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

    private fun addWorkerFactory(className: String = "CalculatePiWorkerFactory"): KtClass {
        val file = myFixture.addFileToProject("com/example/Factory.kt", produceWorkerFactory(className)) as KtFile
        return file.findDescendantOfType<KtClass> { it.name == className }!!
    }

    @Language("kotlin")
    private fun produceCodeWithWorkerCalls(workerName: String) = """
        package com.example.site

        import com.example.$workerName

        fun useWorker() {
            val worker = $workerName { output -> println(output) }
            worker.postInput("hello")
        }
    """.trimIndent()

    private fun addCodeWithWorkerCalls(workerName: String = "CalculatePiWorker"): PsiFile {
        return myFixture.addFileToProject("com/example/site/Usage.kt", produceCodeWithWorkerCalls(workerName))
    }

    // NOTE: Intentionally not wrapped in `allowAnalysisOnEdt`, since in-place renames run on the UI thread, where Kotlin
    // analysis isn't allowed
    private fun rename(factory: KtClass, newName: String) {
        myFixture.renameElement(factory, newName)
    }

    fun testRenamingFactoryRenamesWorkerUsages() {
        val factory = addWorkerFactory()
        val usageFile = addCodeWithWorkerCalls()

        rename(factory, "PiWorkerFactory")

        assertThat(factory.name).isEqualTo("PiWorkerFactory")
        assertThat(usageFile.text).isEqualTo(produceCodeWithWorkerCalls("PiWorker"))
    }

    fun testInPlaceRenamingFactoryRenamesWorkerUsages() {
        // Users typically rename by typing the new name directly in the editor, which takes a different code path than
        // the Rename dialog
        myFixture.configureByText("Factory.kt", produceWorkerFactory("<caret>CalculatePiWorkerFactory"))
        val usageFile = addCodeWithWorkerCalls()

        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        CodeInsightTestUtil.doInlineRename(MemberInplaceRenameHandler(), "PiWorkerFactory", myFixture)

        assertThat(usageFile.text).isEqualTo(produceCodeWithWorkerCalls("PiWorker"))
    }

    private fun configureCodeWithWorkerCallsAtCaret(): PsiFile {
        return myFixture.configureByText(
            "Usage.kt",
            produceCodeWithWorkerCalls("CalculatePiWorker").replace("= CalculatePiWorker", "= <caret>CalculatePiWorker")
        )
    }

    fun testRenamingWorkerDirectlyIsRefused() {
        addWorkerFactory()
        val usageFile = configureCodeWithWorkerCallsAtCaret()

        val e = assertThrows<CommonRefactoringUtil.RefactoringErrorHintException> {
            myFixture.renameElementAtCaret("PiWorker")
        }
        assertThat(e.message!!).isEqualTo(
            "`CalculatePiWorker` is generated from `CalculatePiWorkerFactory`, so rename that class instead."
        )
        assertThat(usageFile.text).isEqualTo(produceCodeWithWorkerCalls("CalculatePiWorker"))
    }

    fun testInPlaceRenamingWorkerDirectlyIsRefused() {
        addWorkerFactory()
        val usageFile = configureCodeWithWorkerCallsAtCaret()

        // NOTE: We trigger the real Rename action here (instead of a specific rename handler), since from a constructor
        // call like this one, the IDE actually targets the worker's constructor, not its class
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        assertThrows<CommonRefactoringUtil.RefactoringErrorHintException> {
            myFixture.performEditorAction(IdeActions.ACTION_RENAME)
        }
        assertThat(TemplateManagerImpl.getTemplateState(myFixture.editor)).isNull()
        assertThat(usageFile.text).isEqualTo(produceCodeWithWorkerCalls("CalculatePiWorker"))
    }

    fun testRenamingWorkerDirectlyIsRefusedIfFqcnSetsClassName() {
        setWorkerFqcn(".CalculatePiWorker")
        addWorkerFactory()
        configureCodeWithWorkerCallsAtCaret()

        val e = assertThrows<CommonRefactoringUtil.RefactoringErrorHintException> {
            myFixture.renameElementAtCaret("PiWorker")
        }
        assertThat(e.message!!).contains("`fqcn`")
    }

    fun testWorkerUsagesUnchangedIfFqcnSetsClassName() {
        // The worker's name doesn't depend on the factory's name in this case
        setWorkerFqcn(".CalculatePiWorker")
        val factory = addWorkerFactory()
        val usageFile = addCodeWithWorkerCalls()

        rename(factory, "PiWorkerFactory")

        assertThat(usageFile.text).isEqualTo(produceCodeWithWorkerCalls("CalculatePiWorker"))
    }
}
