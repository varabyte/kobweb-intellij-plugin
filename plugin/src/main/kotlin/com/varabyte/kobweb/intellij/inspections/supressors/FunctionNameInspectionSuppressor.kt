package com.varabyte.kobweb.intellij.inspections.supressors

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.compose.COMPOSABLE_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.isInKobwebSource
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.psi.hasAnyAnnotation
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Suppress the "Function name should start with a lowercase letter" inspection.
 */
class FunctionNameInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != "FunctionName") return false
        if (!element.isInReadableKobwebProject() && !element.isInKobwebSource()) return false
        val ktFunction = element.parent as? KtNamedFunction ?: return false

        return analyze(ktFunction) { ktFunction.hasAnyAnnotation(COMPOSABLE_CLASS_ID) }
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String) = emptyArray<SuppressQuickFix>()
}
