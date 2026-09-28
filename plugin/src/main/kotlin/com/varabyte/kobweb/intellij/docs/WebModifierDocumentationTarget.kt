package com.varabyte.kobweb.intellij.docs

import com.intellij.documentation.mdn.MdnApiNamespace
import com.intellij.documentation.mdn.MdnCssSymbolKind
import com.intellij.documentation.mdn.getCssMdnDocumentation
import com.intellij.documentation.mdn.getDomEventDocumentation
import com.intellij.documentation.mdn.getHtmlMdnAttributeDocumentation
import com.intellij.documentation.mdn.getJsMdnDocumentation
import com.intellij.icons.AllIcons
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebName
import com.varabyte.kobweb.intellij.util.text.camelCaseToKebabCase
import org.jetbrains.eval4j.ThrownFromEvalExceptionBase

// If docs classes ever change in the future, we can always delete this feature as it is pretty minor
@Suppress("UnstableApiUsage")
class WebModifierDocumentationTarget(private val webName: WebName, val element: PsiElement) : DocumentationTarget {
    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(webName.asString())
            .icon(when (webName) {
                is WebName.Html -> AllIcons.FileTypes.Html
                is WebName.Css -> AllIcons.FileTypes.Css
            })
            .presentation()

    override fun computeDocumentation(): DocumentationResult {
        fun WebName.docsNotFoundMessage(): String {
            val categoryType = when (this) {
                is WebName.Html -> "HTML"
                is WebName.Css -> "CSS"
            }
            val webNameType = when (this) {
                is WebName.Attribute -> "attribute"
                is WebName.StyleProperty -> "style"
                is WebName.Event -> "event"
            }

            return "No documentation found for $categoryType $webNameType <code style=\"white-space:nowrap\">${this.asString()}</code>.<ul><li><a href=\"https://developer.mozilla.org/en-US/search?q=${this.asString()}\">Search the official docs</a>.</li><li>Consider <a href=\"https://github.com/varabyte/kobweb-intellij-plugin/issues/new?title=Missing+docs+for+${webNameType}+`${this.asString()}`&body=(You+can+just+hit+Create)\">filing an issue against the Kobweb Plugin</a> if you think it should support it.</li></ul>"
        }

        fun WebName.docsNotFoundResult() = DocumentationResult.documentation(this.docsNotFoundMessage())

        val mdnDoc = when (webName) {
            is WebName.Attribute -> {
                getHtmlMdnAttributeDocumentation(MdnApiNamespace.Html, tagName = null, webName.asString())
            }
            is WebName.Event -> {
                getDomEventDocumentation(webName.asString())
            }
            is WebName.StyleProperty -> {
                getCssMdnDocumentation(webName.asString(), MdnCssSymbolKind.Property)
            }
        } ?: return webName.docsNotFoundResult()
        val html = mdnDoc.getDocumentation(withDefinition = true)
        return DocumentationResult.documentation(html).externalUrl(mdnDoc.url)
    }

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val elementPointer = element.createSmartPointer()

        return Pointer {
            elementPointer.element?.let {
                WebModifierDocumentationTarget(webName, it)
            }
        }
    }
}