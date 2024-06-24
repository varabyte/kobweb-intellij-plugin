package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.psi.hasAnyAnnotation
import org.jetbrains.kotlin.psi.KtNamedFunction

private val ANNOTATION_GENERATES_CODE_KEY = Key<CachedValue<Boolean>>("ANNOTATION_GENERATES_CODE")

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
        if (!element.isInReadableKobwebProject()) return false
        val ktFunction = element.parent as? KtNamedFunction ?: return false

        return ktFunction.hasAnyAnnotation(ANNOTATION_GENERATES_CODE_KEY, *SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH)
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String) = emptyArray<SuppressQuickFix>()
}
