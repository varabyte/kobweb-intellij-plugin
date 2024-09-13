package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.varabyte.kobweb.intellij.util.kobweb.isInKobwebSource
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.psi.hasAnyAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtNamedFunction

private val IS_COMPOSABLE_KEY = Key<CachedValue<Boolean>>("IS_COMPOSABLE")
private val COMPOSABLE_ANNOTATION_ID = ClassId.fromString("androidx/compose/runtime/Composable")

/**
 * Suppress the "Function name should start with a lowercase letter" inspection.
 */
class FunctionNameInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != "FunctionName") return false
        if (!element.isInReadableKobwebProject() && !element.isInKobwebSource()) return false
        val ktFunction = element.parent as? KtNamedFunction ?: return false

        return ktFunction.hasAnyAnnotation(IS_COMPOSABLE_KEY, COMPOSABLE_ANNOTATION_ID)
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String) = emptyArray<SuppressQuickFix>()
}
