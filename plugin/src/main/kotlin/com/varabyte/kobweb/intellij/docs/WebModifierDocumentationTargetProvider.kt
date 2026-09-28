package com.varabyte.kobweb.intellij.docs

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.util.kobweb.isUsedInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebName
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebNames
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierChainingExtension
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinPsiDocumentationTargetProvider
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import kotlin.collections.get

// Although our plugin is usually good at surfacing styles and properties, we may occasionally need to intercept some
// special-cases.
private val WEB_MODIFIER_NAME_OVERRIDES: Map<String, List<WebName>> = mapOf(
    "classNames" to listOf(WebName.Attribute("class")),
    "dataAttr" to listOf(WebName.Attribute("data")),
    "dataAttrs" to listOf(WebName.Attribute("data")),
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
        element: PsiElement, // The resolved element that the docs are attached to
        originalElement: PsiElement?, // The actual element in the current editor that references `element`
    ): List<DocumentationTarget> {
        return kotlinDocProvider.documentationTargets(element, originalElement) +
                if (originalElement != null && !originalElement.isUsedInReadableKobwebProject()) {
                    emptyList()
                } else {
                    webDocumentationTargetsFor(element, originalElement)
                }
    }

    private fun webDocumentationTargetsFor(element: PsiElement, originalElement: PsiElement?): List<DocumentationTarget> {
        val function = element as? KtNamedFunction ?: return emptyList()

        analyze(function) {
            if (function.isModifierChainingExtension()) {
                return function.createDocumentationTargets()
            }
        }

        // If here, we MIGHT be inside a modifier function scope, e.g. something like "color" and "size" in
        // Modifier.background {
        //   color(Colors.Magenta)
        //   size(BackgroundSize.Contain)
        // }
        // At this point, we need to run up the PSI tree searching for a parent call expression that is a modifier
        // chain.

        val callSite = originalElement?.let { PsiTreeUtil.getParentOfType(it, KtCallExpression::class.java) } ?: return emptyList()
        var currentCall: KtCallExpression? = callSite
        analyze(callSite) {
            while (currentCall != null) {
                (currentCall.calleeExpression
                    ?.mainReference
                    ?.resolve() as? KtNamedFunction)
                    ?.takeIf { it.isModifierChainingExtension() }
                    ?.let { return function.createDocumentationTargets() }

                currentCall = PsiTreeUtil.getParentOfType(currentCall, KtCallExpression::class.java)
            }
        }
        return emptyList()
    }

    private fun KtNamedFunction.createDocumentationTargets(): List<WebModifierDocumentationTarget> {
        val webNames = WEB_MODIFIER_NAME_OVERRIDES[name] ?: getWebNames()
        return webNames.map { webName -> WebModifierDocumentationTarget(webName, this) }
    }
}