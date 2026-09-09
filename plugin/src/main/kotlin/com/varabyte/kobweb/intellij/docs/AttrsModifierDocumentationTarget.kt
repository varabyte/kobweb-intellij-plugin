package com.varabyte.kobweb.intellij.docs

import com.intellij.documentation.mdn.MdnApiNamespace
import com.intellij.documentation.mdn.MdnCssSymbolKind
import com.intellij.documentation.mdn.getCssMdnDocumentation
import com.intellij.documentation.mdn.getHtmlMdnAttributeDocumentation
import com.intellij.icons.AllIcons
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer

// If docs classes ever change in the future, we can always delete this feature as it is pretty minor
@Suppress("UnstableApiUsage")
class AttrsModifierDocumentationTarget(kotlinName: String, val element: PsiElement) : DocumentationTarget {
    val htmlAttrName = kotlinName.lowercase()

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(htmlAttrName)
            .icon(AllIcons.FileTypes.Html)
            .presentation()

    override fun computeDocumentation(): DocumentationResult? {
        val possibleNamespaces = listOf(MdnApiNamespace.Html, MdnApiNamespace.DomEvents)

        val mdnDoc =
            possibleNamespaces.asSequence()
                .map { getHtmlMdnAttributeDocumentation(it, tagName = null, htmlAttrName) }
                .firstOrNull()
                ?: return null
        val html = mdnDoc.getDocumentation(withDefinition = true)
        return DocumentationResult.documentation(html).externalUrl(mdnDoc.url)
    }

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val elementPointer = element.createSmartPointer()

        return Pointer {
            elementPointer.element?.let {
                AttrsModifierDocumentationTarget(htmlAttrName, it)
            }
        }
    }
}