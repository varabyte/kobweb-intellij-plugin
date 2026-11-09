@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.icons

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language

class MaterialIconsInspectionTest : KobwebApplicationTestCase() {
    // Runs the inspection directly, since the test fixture refuses to highlight build scripts
    private fun inspectBuildScript(@Language("kotlin") code: String): List<ProblemDescriptor> {
        val file = myFixture.addFileToProject("build.gradle.kts", code)
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, /* onTheFly = */ false)
        val visitor = MaterialIconsInspection().buildVisitor(holder, /* isOnTheFly = */ false)
        PsiTreeUtil.processElements(file) { element ->
            element.accept(visitor)
            true
        }
        return holder.results
    }

    fun testReportsMaterialIcons() {
        val problems = inspectBuildScript(
            """
            kobweb {
                app {
                    icons {
                        fa()
                        ms()
                        mdi {
                        }
                    }
                }
            }
            """.trimIndent()
        )

        assertThat(problems.map { it.psiElement.text }).containsExactly("mdi")
    }

    fun testIgnoresMdiOutsideIconsBlock() {
        val problems = inspectBuildScript(
            """
            kobweb {
                library {
                    mdi()
                }
            }
            """.trimIndent()
        )

        assertThat(problems).isEmpty()
    }
}
