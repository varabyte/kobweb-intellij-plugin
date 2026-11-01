package com.varabyte.kobweb.intellij.inspections.supressors

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.isInKobwebSource
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSheetSingletonClassId
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

/**
 * Suppress the "Object or top-level property name should not start with an underscore" inspection for private styles,
 * e.g. `private val _MyStyle = CssStyle { }`, as Kobweb recommends that name (see `PrivateStyleSingletonsInspection`).
 */
class ObjectPropertyNameInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != "ObjectPropertyName") return false
        if (!element.isInReadableKobwebProject() && !element.isInKobwebSource()) return false
        val property = element.parent as? KtProperty ?: element as? KtProperty ?: return false
        if (!property.isPrivate() || property.name?.startsWith("_") != true) return false

        return analyze(property) { property.initializer?.expressionType?.styleSheetSingletonClassId != null }
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String) = emptyArray<SuppressQuickFix>()
}
