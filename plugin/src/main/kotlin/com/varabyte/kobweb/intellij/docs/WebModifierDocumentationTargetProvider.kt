package com.varabyte.kobweb.intellij.docs

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierChainingExtension
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinPsiDocumentationTargetProvider
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtNamedFunction

// In a few cases, Kobweb may have taken some liberties with their chosen names, e.g., for clarity or to mimic
// Jetpack Compose or to avoid conflicts with Kotlin keywords.
private val WEB_MODIFIER_NAME_OVERRIDES: Map<String, List<String>> = mapOf(
    "classNames" to listOf("class"),
    "fillMaxWidth" to listOf("width"),
    "fillMaxHeight" to listOf("height"),
    "fillMaxSize" to listOf("width", "height"),
    "size" to listOf("width", "height"),
    "minSize" to listOf("minWidth", "minHeight"),
    "maxSize" to listOf("maxWidth", "maxHeight"),
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
        return kotlinDocProvider.documentationTargets(element, originalElement) + webDocumentationTargetsFor(element)
    }

    private fun webDocumentationTargetsFor(element: PsiElement): List<DocumentationTarget> {
        val function = element.asKtNamedFunction() ?: return emptyList()

        analyze(function) {
            if (!function.isModifierChainingExtension()) return emptyList()
        }

        val webModifierType = function.getWebModifierType().takeUnless { it == WebModifierType.UNKNOWN } ?: return emptyList()

        fun String.nameOverrides(): List<String> = WEB_MODIFIER_NAME_OVERRIDES[this] ?: listOf(this)
        val propertyNames = function.name?.nameOverrides() ?: return emptyList()

        return when (webModifierType) {
            WebModifierType.STYLE -> propertyNames.map { propertyName -> StyleModifierDocumentationTarget(propertyName, element) }
            WebModifierType.ATTRS -> propertyNames.map { propertyName -> AttrsModifierDocumentationTarget(propertyName, element) }
            else -> error("Unexpected modifier type: $webModifierType") // We should have early aborted before this point
        }
    }
}