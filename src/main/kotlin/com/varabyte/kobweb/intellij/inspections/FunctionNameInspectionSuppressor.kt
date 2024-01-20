package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.annotationClassIds
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.psi.KtNamedFunction

private val SUPPRESS_FUNCTION_NAME_FOR = arrayOf(
    "androidx.compose.runtime.Composable",
)

/**
 * Suppress the "Function name should start with a lowercase letter" inspection.
 */
class FunctionNameInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != "FunctionName") return false
        val ktFunction = element.parent as? KtNamedFunction ?: return false

        analyze(ktFunction) {
            val symbol = ktFunction.getSymbol()

            symbol.annotationClassIds.forEach {
                if (it.asFqNameString() in SUPPRESS_FUNCTION_NAME_FOR) return true
            }
        }

        return false
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String) = emptyArray<SuppressQuickFix>()
}
