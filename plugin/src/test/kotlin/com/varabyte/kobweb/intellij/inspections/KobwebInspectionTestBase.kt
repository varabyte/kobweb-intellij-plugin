package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.kobweb.intellij.test.util.checkResultAndHighlight
import org.intellij.lang.annotations.Language
import kotlin.reflect.KClass

abstract class KobwebInspectionTestBase : KobwebApplicationTestCase() {
    protected inner class PostInspectionFix(
        private val applyFix: () -> Unit,
        private val newCode: String
    ) {
        fun invoke() {
            applyFix()
            myFixture.checkResultAndHighlight(newCode)
        }
    }

    private fun findQuickFixes(quickFixClass: KClass<out LocalQuickFix>): List<IntentionAction> {
        return myFixture.getAllQuickFixes()
            .filter {
                val quickFix = QuickFixWrapper.unwrap(it)
                quickFix != null && quickFix::class == quickFixClass
            }.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("No matching quick fix found for ${quickFixClass.simpleName}")
    }

    protected inline fun <reified F: LocalQuickFix> thenFixWith(@Language("kotlin") newCode: String): PostInspectionFix {
        return thenFixWith(F::class, newCode)
    }

    // NOTE: The lambdas live in non-inline functions, since lambdas inlined into a subclass can't call protected
    // methods
    protected fun thenFixWith(quickFixClass: KClass<out LocalQuickFix>, newCode: String): PostInspectionFix {
        return PostInspectionFix({ findQuickFixes(quickFixClass).forEach { myFixture.launchAction(it) } }, newCode)
    }

    /**
     * Like [thenFixWith], but for fixes that need more than just being launched, e.g. ones that ask the user to choose
     * from a popup, which tests can't show.
     *
     * @param applyFix Called with the quick fix found for the inspection's problem, as well as that problem.
     */
    protected inline fun <reified F: LocalQuickFix> thenFixWith(
        noinline applyFix: F.(ProblemDescriptor) -> Unit,
        @Language("kotlin") newCode: String,
    ): PostInspectionFix {
        return thenFixWith(F::class, applyFix, newCode)
    }

    protected fun <F: LocalQuickFix> thenFixWith(
        quickFixClass: KClass<F>,
        applyFix: F.(ProblemDescriptor) -> Unit,
        newCode: String,
    ): PostInspectionFix {
        return PostInspectionFix(
            {
                // The fixture's quick fixes don't expose the problem they fix, so get it from the inspection directly
                val problems = ProgressManager.getInstance().runProcess<List<ProblemDescriptor>>(
                    {
                        (produceInspection() as LocalInspectionTool)
                            .processFile(myFixture.file, InspectionManager.getInstance(project))
                    },
                    EmptyProgressIndicator(),
                )
                val (problem, quickFix) = problems
                    .flatMap { problem ->
                        problem.fixes.orEmpty().filterIsInstance(quickFixClass.java).map { fix -> problem to fix }
                    }
                    .single()
                quickFix.applyFix(problem)
            },
            newCode
        )
    }

    protected abstract fun produceInspection(): InspectionProfileEntry

    protected fun doTest(
        @Language("kotlin") code: String,
        thenFix: PostInspectionFix? = null
    ) {
        myFixture.enableInspections(produceInspection())
        myFixture.configureByText("KobwebInspectionTest.kt", code)
        myFixture.checkHighlighting(true, false, false)

        thenFix?.invoke()
    }
}
