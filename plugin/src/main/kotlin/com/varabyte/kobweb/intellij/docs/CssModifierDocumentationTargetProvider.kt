package com.varabyte.kobweb.intellij.docs

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierChainingExtension
import com.varabyte.kobweb.intellij.util.text.camelCaseToKebabCase
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinPsiDocumentationTargetProvider
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtNamedFunction

/**
 * Provides documentation for CSS modifier functions tied to CSS properties.
 *
 * For example, `Modifier.backgroundColor(...)` will show the MDN documentation for the `background-color` CSS property
 * on the second page.
 */
class CssModifierDocumentationTargetProvider : PsiDocumentationTargetProvider {
    val kotlinDocProvider = KotlinPsiDocumentationTargetProvider()

    /**
     * @param element         the element for which the documentation is requested (for example, if the mouse is over
     *                        a method reference, this will be the method to which the reference is resolved).
     * @param originalElement the element under the mouse cursor
     */
    override fun documentationTargets(
        element: PsiElement,
        originalElement: PsiElement?,
    ): List<DocumentationTarget> {
        return kotlinDocProvider.documentationTargets(element, originalElement) +
                listOfNotNull(documentationTarget(element))
    }

    private fun documentationTarget(element: PsiElement): DocumentationTarget? {
        val function = element.asKtNamedFunction() ?: return null
        val propertyName = function.name?.camelCaseToKebabCase() ?: return null

        analyze(function) {
            if (!function.isModifierChainingExtension()) return null
        }
        if (function.getWebModifierType() != WebModifierType.STYLE) return null

        return CssModifierDocumentationTarget(propertyName, element)
    }
}