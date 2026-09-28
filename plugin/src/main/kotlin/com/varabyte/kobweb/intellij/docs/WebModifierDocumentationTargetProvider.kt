package com.varabyte.kobweb.intellij.docs

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.isUsedInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebName
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebNames
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierChainingExtension
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinPsiDocumentationTargetProvider
import org.jetbrains.kotlin.psi.KtNamedFunction

// Although our plugin is usually good at surfacing styles and properties, we may occasionally need to intercept some
// special-cases.
private val WEB_MODIFIER_NAME_OVERRIDES: Map<String, List<WebName>> = mapOf(
    "classNames" to listOf(WebName.Attribute("class")),
)

/**
 * Provides documentation for CSS modifier functions tied to CSS properties.
 *
 * For example, `Modifier.backgroundColor(...)` will show the MDN documentation for the `background-color` CSS property
 * on the second page.
 */
class WebModifierDocumentationTargetProvider : PsiDocumentationTargetProvider {
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
        if (!element.isUsedInReadableKobwebProject()) return emptyList()
        return kotlinDocProvider.documentationTargets(element, originalElement) + webDocumentationTargetsFor(element)
    }

    private fun webDocumentationTargetsFor(element: PsiElement): List<DocumentationTarget> {
        val function  = element as? KtNamedFunction ?: return emptyList()
        analyze(function) {
            if (!function.isModifierChainingExtension()) return emptyList()
        }

        val webNames = WEB_MODIFIER_NAME_OVERRIDES[function.name] ?: function.getWebNames()

        return webNames.map { webName -> WebModifierDocumentationTarget(webName, function) }
    }
}