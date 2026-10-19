package com.varabyte.kobweb.intellij.findUsages.worker

import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.varabyte.kobweb.intellij.test.fixtures.KobwebWorkerTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
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

    /** Returns the (trimmed) line of code this usage appears on, with the usage itself wrapped in brackets. */
    private fun UsageInfo2UsageAdapter.toLineWithUsageMarked(): String {
        val document = PsiDocumentManager.getInstance(project).getDocument(usageInfo.file!!)!!
        val usageRange = usageInfo.segment!!
        val line = document.getLineNumber(usageRange.startOffset)
        val lineStart = document.getLineStartOffset(line)
        val lineText = document.getText(TextRange(lineStart, document.getLineEndOffset(line)))

        val start = usageRange.startOffset - lineStart
        val end = usageRange.endOffset - lineStart
        return (lineText.substring(0, start) + "[" + lineText.substring(start, end) + "]" + lineText.substring(end)).trim()
    }

    /**
     * Find usages of the target element, the same way the Find Usages action does (which includes custom searchers).
     *
     * @return Each usage found, as the line of code it appears on (with the usage itself in brackets), prefixed with its
     *   file name. For example: `"Usage.kt: val worker = [CalculatePiWorker] { ... }"`. This mirrors what the Find Usages
     *   window shows.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun findUsages(element: PsiElement): List<String> {
        val findUsagesManager = (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager
        val handler = findUsagesManager.getFindUsagesHandler(element, false)!!
        val searcher = FindUsagesManager.createUsageSearcher(
            handler,
            handler.primaryElements,
            handler.secondaryElements,
            handler.getFindUsagesOptions(null),
        )

        val usages = mutableListOf<Usage>()
        allowAnalysisOnEdt {
            // Usage searches expect to run with a progress indicator, as they do in the IDE
            ProgressManager.getInstance().runProcess(
                { searcher.generate { usage -> usages.add(usage); true } },
                EmptyProgressIndicator(),
            )
        }
        return usages
            .filterIsInstance<UsageInfo2UsageAdapter>()
            .map { usage -> "${usage.file.name}: ${usage.toLineWithUsageMarked()}" }
            .sorted()
    }

    fun testFactoryUsagesIncludeWorkerUsages() {
        val factory = addWorkerFactory()
        addCodeWithWorkerCalls()

        assertThat(findUsages(factory)).containsExactly(
            "Usage.kt: import com.example.[CalculatePiWorker]",
            "Usage.kt: val worker = [CalculatePiWorker] { output -> println(output) }",
        )
    }

    fun testNoWorkerUsagesIfMultipleFactories() {
        // Kobweb doesn't generate a worker if there are multiple factories, so there's no worker to find usages of
        val factory = addWorkerFactory()
        addWorkerFactory("OtherCalculateWorkerFactory", "com/example/OtherFactory.kt")
        addCodeWithWorkerCalls()

        assertThat(findUsages(factory)).isEmpty()
    }
}
