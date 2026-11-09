@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.icons

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.quickfixes.icons.FetchIconsQuickFix
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language

class UnfetchedIconSetInspectionTest : KobwebApplicationTestCase() {
    // Runs the inspection directly, since the test fixture refuses to highlight build scripts
    private fun inspectBuildScript(@Language("kotlin") code: String): List<ProblemDescriptor> {
        val file = myFixture.addFileToProject("build.gradle.kts", code)
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, /* onTheFly = */ false)
        val visitor = UnfetchedIconSetInspection().buildVisitor(holder, /* isOnTheFly = */ false)
        PsiTreeUtil.processElements(file) { element ->
            element.accept(visitor)
            true
        }
        return holder.results
    }

    fun testReportsIconSetsWithoutLockFiles() {
        myFixture.addFileToProject("kobweb-store/icons/fa.lock", "spec=6.+\nversion=6.5.2\n")

        val problems = inspectBuildScript(
            """
            kobweb {
                app {
                    icons {
                        fa()
                        lucide()
                        mdi {
                        }
                    }
                }
            }
            """.trimIndent()
        )

        assertThat(problems.map { it.psiElement.text }).containsExactly("lucide", "mdi").inOrder()
    }

    fun testOffersToFetchIcons() {
        val problems = inspectBuildScript(
            """
            kobweb {
                library {
                    icons {
                        ms()
                    }
                }
            }
            """.trimIndent()
        )

        assertThat(problems.single().fixes!!.single()).isInstanceOf<FetchIconsQuickFix>()
    }

    fun testIgnoresIconSetsOutsideIconsBlock() {
        val problems = inspectBuildScript(
            """
            kobweb {
                app {
                    fa()
                }
            }
            """.trimIndent()
        )

        assertThat(problems).isEmpty()
    }
}
