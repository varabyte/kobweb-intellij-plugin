package com.varabyte.kobweb.intellij.docs

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.documentation.mdn.*
import com.intellij.icons.AllIcons
import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiElement
import com.intellij.util.io.HttpRequests
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebName
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

private const val MDN_DOCS_BASE = "https://developer.mozilla.org/en-US/docs"

private object MdnDocFetcher {
    private const val NETWORK_TIMEOUT_MS = 3000

    private val objectMapper = ObjectMapper()

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .maximumSize(100)
        .build<String, String>()

    fun cached(name: String): String? {
        return cache.getIfPresent(name)
    }

    /**
     * Network call that must NOT be triggered on the EDT thread.
     */
    fun fetch(name: String, url: String): String? {
        return cache.get(name) { attr ->
            try {
                val responseJson = HttpRequests.request(url)
                    .accept("application/json")
                    .connectTimeout(NETWORK_TIMEOUT_MS)
                    .readTimeout(NETWORK_TIMEOUT_MS)
                    .readString()

                val rootNode = objectMapper.readTree(responseJson)


                val docHtml = rootNode.path("doc").path("body").firstOrNull {
                    it.path("type").asText() == "prose" && it.path("value").path("id").asText() == "description"
                }?.let { node -> node.path("value").path("content").asText().takeIf { it.isNotBlank() } }

                docHtml
            } catch (_: Exception) {
                null
            }
        }
    }
}

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
        val fetchedContent = MdnDocFetcher.cached(name) ?: runBlockingCancellable {
            withBackgroundProgress(element.project, "Fetching MDN documentation...", cancellable = true) {
                val future = ApplicationManager.getApplication().executeOnPooledThread<String?> {
                    MdnDocFetcher.fetch(name, "$url/index.json")
                }
                future.get()
            }
        }
        val docsContent = fetchedContent ?:
            "Please see the <a href=$MDN_DOCS_BASE/$urlSuffix>MDN docs for <code>$name</code></a> for more information."

        return object : MdnSymbolDocumentation {
            override val name: String = name
            override val url: String = url
            // We have to stub out apiStatus, an issue due to us targeting older IDEs. Since we never call it, it's OK.
            override val apiStatus get() = error("not defined")
            override val description = docsContent

            override val sections = emptyMap<String, String>()
            override val footnote: String = "By <a href='https://developer.mozilla.org/'>Mozilla Contributors</a>, " +
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
        createFallbackHtmlDocumentation(name, "Web/Accessibility/ARIA/Attributes/$name")

    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)
}