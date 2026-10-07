package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import org.intellij.lang.annotations.Language
import kotlin.reflect.KClass

abstract class KobwebInspectionTestBase : KobwebApplicationTestCase() {
    protected inner class PostInspectionFix<F : LocalQuickFix>(
        private val quickFixClass: KClass<F>,
        private val newCode: String
    ) {
        fun invoke() {
            val quickFixes = myFixture.getAllQuickFixes()
                .filter {
                    val quickFix = QuickFixWrapper.unwrap(it)
                    quickFix != null && quickFix::class == quickFixClass
                }.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("No matching quick fix found for ${quickFixClass.simpleName}")

            quickFixes.forEach { quickFix ->
                myFixture.launchAction(quickFix)
            }
            myFixture.checkResult(newCode)
        }
    }

    protected inline fun <reified F: LocalQuickFix> thenFixWith(@Language("kotlin") newCode: String): PostInspectionFix<F> {
        return PostInspectionFix(F::class, newCode)
    }

    protected abstract fun produceInspection(): InspectionProfileEntry

    protected fun doTest(
        @Language("kotlin") code: String,
    ) {
        doTest<LocalQuickFix>(code, null)
    }

    protected fun <F : LocalQuickFix> doTest(
        @Language("kotlin") code: String,
        thenFix: PostInspectionFix<F>? = null
    ) {
        myFixture.enableInspections(produceInspection())
        myFixture.configureByText("KobwebInspectionTest.kt", code)
        myFixture.checkHighlighting(true, false, false)

        thenFix?.invoke()
    }
}
