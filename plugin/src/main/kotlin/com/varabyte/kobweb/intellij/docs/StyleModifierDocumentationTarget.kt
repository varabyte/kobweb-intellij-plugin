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

    override fun computeDocumentation(): DocumentationResult? {
        val mdnDoc = getCssMdnDocumentation(cssPropertyName, MdnCssSymbolKind.Property)
            ?: return null
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