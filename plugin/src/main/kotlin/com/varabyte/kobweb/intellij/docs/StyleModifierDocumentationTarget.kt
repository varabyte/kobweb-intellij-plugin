package com.varabyte.kobweb.intellij.docs

import com.intellij.documentation.mdn.MdnCssSymbolKind
import com.intellij.documentation.mdn.getCssMdnDocumentation
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.varabyte.kobweb.intellij.util.text.camelCaseToKebabCase

// If docs classes ever change in the future, we can always delete this feature as it is pretty minor
@Suppress("UnstableApiUsage")
class StyleModifierDocumentationTarget(kotlinName: String, val element: PsiElement) : DocumentationTarget {
    val cssPropertyName = kotlinName.camelCaseToKebabCase()

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(cssPropertyName)
            .icon(com.intellij.icons.AllIcons.FileTypes.Css)
            .presentation()

    override fun computeDocumentation(): DocumentationResult {
        val mdnDoc = getCssMdnDocumentation(cssPropertyName, MdnCssSymbolKind.Property)
            ?: return DocumentationResult.documentation("No documentation found for CSS style <code style=\"white-space:nowrap\">$cssPropertyName</code>.<ul><li><a href=\"https://developer.mozilla.org/en-US/search?q=$cssPropertyName\">Search the official docs</a>.</li><li>Consider <a href=\"https://github.com/varabyte/kobweb-intellij-plugin/issues/new?title=Missing+docs+for+style+`$cssPropertyName`&body=(You+can+just+hit+Create)\">filing an issue against the Kobweb Plugin</a> if you think it should support it.</li></ul>")
        val html = mdnDoc.getDocumentation(withDefinition = true)
        return DocumentationResult.documentation(html).externalUrl(mdnDoc.url)
    }

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val elementPointer = element.createSmartPointer()

        return Pointer {
            elementPointer.element?.let {
                StyleModifierDocumentationTarget(cssPropertyName, it)
            }
        }
    }
}