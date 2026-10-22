package com.varabyte.kobweb.intellij.docs

import com.intellij.documentation.mdn.*
import com.intellij.icons.AllIcons
import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.services.docs.MdnDocsService
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebName
import java.util.function.Consumer

private const val MDN_DOCS_BASE = "https://developer.mozilla.org/en-US/docs"

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
                val webNameStr = webName.asString()
                getHtmlMdnAttributeDocumentation(MdnApiNamespace.Html, tagName = null, webName.asString()) ?: when {
                    webNameStr.startsWith("aria-") -> getHtmlAriaDocumentation(webNameStr)
                    webNameStr == "data" || webNameStr.startsWith("data-") -> getHtmlDataAttributeDocumentation(webNameStr)
                    else -> null
                }
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

    // Some MDN sections aren't provided by the IntelliJ platform APIs, it seems. We can provide fallback docs in that
    // case.
    private fun createFallbackHtmlDocumentation(name: String, urlSuffix: String): MdnSymbolDocumentation {
        val url = "$MDN_DOCS_BASE/$urlSuffix"

        // Avoid doing a network fetch while on the EDT or inside a read action
        val fetchedContent = MdnDocsService.instance.cached(name) ?: runBlockingCancellable {
            withBackgroundProgress(element.project, "Fetching MDN documentation...", cancellable = true) {
                val future = ApplicationManager.getApplication().executeOnPooledThread<String?> {
                    MdnDocsService.instance.fetch(name, "$url/index.json")
                }
                future.get()
            }
        }
        val docsContent = fetchedContent ?:
            "Please see the <a href=$MDN_DOCS_BASE/$urlSuffix>MDN docs for <code>$name</code></a> for more information."

        return object : MdnSymbolDocumentation {
            override val name: String = name
            override val url: String = url
            override val apiStatus = PolySymbolApiStatus.Stable
            override val description = docsContent

            override val sections = emptyMap<String, String>()
            override val footnote: String = "By <a href='$url/contributors.txt'>Mozilla Contributors</a>, " +
                    "<a href='https://creativecommons.org/licenses/by-sa/2.5/'>CC BY-SA 2.5</a>"

            override fun getDocumentation(withDefinition: Boolean) = getDocumentation(withDefinition, null)

            override fun getDocumentation(
                withDefinition: Boolean,
                additionalSectionsContent: Consumer<StringBuilder>?
            ): String {
                return buildString {
                    if (withDefinition) {
                        append("<div class='definition'><code>$name</code></div>")
                    }
                    append("<div class='content'>$description</div>")
                    append("<hr/>")
                    append("<div class='footer'>$footnote</div>")
                }
            }
        }
    }

    private fun getHtmlAriaDocumentation(name: String) =
        createFallbackHtmlDocumentation(name, "Web/Accessibility/ARIA/Reference/Attributes/$name")

    private fun getHtmlDataAttributeDocumentation(name: String) =
        createFallbackHtmlDocumentation(name, "Web/HTML/How_to/Use_data_attributes")

    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)
}