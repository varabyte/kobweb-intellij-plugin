package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.annotationClassIds
import org.jetbrains.kotlin.psi.KtNamedFunction

private val SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH = arrayOf(
    "com.varabyte.kobweb.api.Api",
    "com.varabyte.kobweb.api.init.InitApi",
    "com.varabyte.kobweb.core.App",
    "com.varabyte.kobweb.core.Page",
    "com.varabyte.kobweb.core.init.InitKobweb",
    "com.varabyte.kobweb.silk.init.InitSilk",
)

/**
 * Suppress the "Unused code" inspection, when we know that Kobweb will generate code that uses it.
 */
class UnusedInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != "unused") return false
        val ktFunction = element.parent as? KtNamedFunction ?: return false

        analyze(ktFunction) {
            val symbol = ktFunction.getSymbol()

            symbol.annotationClassIds.forEach {
                if (it.asFqNameString() in SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH) return true
            }
        }

        return false
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String) = emptyArray<SuppressQuickFix>()
}
