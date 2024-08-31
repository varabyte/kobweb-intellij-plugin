package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.util.containers.map2Array
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.psi.hasAnyAnnotation
import com.varabyte.kobweb.intellij.util.psi.hasAnyAnnotationK2
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.name.ClassId
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

private val SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH_IDS = SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH
    .map2Array { ClassId.fromString(it.replace('.', '/')) }

/**
 * Suppress the "Unused code" inspection, when we know that Kobweb will generate code that uses it.
 */
class UnusedInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != "unused") return false
        if (!element.isInReadableKobwebProject()) return false
        val ktFunction = element.parent as? KtNamedFunction
            ?: element as? KtNamedFunction // IDK why this is now needed, but seems like it is (is the first check still needed?)
            ?: return false

        return if (KotlinPluginModeProvider.isK2Mode()) {
            ktFunction.hasAnyAnnotationK2(ANNOTATION_GENERATES_CODE_KEY, *SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH_IDS)
        } else {
            ktFunction.hasAnyAnnotation(ANNOTATION_GENERATES_CODE_KEY, *SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH)
        }
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String) = emptyArray<SuppressQuickFix>()
}
