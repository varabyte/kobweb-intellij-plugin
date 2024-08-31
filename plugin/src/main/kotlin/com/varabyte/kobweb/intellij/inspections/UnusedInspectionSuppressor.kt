package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.util.containers.map2Array
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.psi.hasAnyAnnotationK1
import com.varabyte.kobweb.intellij.util.psi.hasAnyAnnotation
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtNamedFunction

private val ANNOTATION_GENERATES_CODE_KEY = Key<CachedValue<Boolean>>("ANNOTATION_GENERATES_CODE")

private val SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH = arrayOf(
    ClassId.fromString("com/varabyte/kobweb/api/Api"),
    ClassId.fromString("com/varabyte/kobweb/api/init/InitApi"),
    ClassId.fromString("com/varabyte/kobweb/core/App"),
    ClassId.fromString("com/varabyte/kobweb/core/Page"),
    ClassId.fromString("com/varabyte/kobweb/core/init/InitKobweb"),
    ClassId.fromString("com/varabyte/kobweb/silk/init/InitSilk"),
)

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
            ktFunction.hasAnyAnnotation(ANNOTATION_GENERATES_CODE_KEY, *SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH)
        } else {
            ktFunction.hasAnyAnnotationK1(ANNOTATION_GENERATES_CODE_KEY, *SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH_FQNS)
        }
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String) = emptyArray<SuppressQuickFix>()
}

// region K1 legacy

private val SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH_FQNS = SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH
    .map2Array { it.asFqNameString() }

// endregion
