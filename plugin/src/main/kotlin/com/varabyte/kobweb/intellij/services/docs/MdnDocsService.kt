package com.varabyte.kobweb.intellij.services.docs

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.io.HttpRequests
import java.util.concurrent.TimeUnit

private const val NETWORK_TIMEOUT_MS = 3000

/**
 * Fetches (and caches) documentation from MDN, for the web names that the IntelliJ platform doesn't provide docs for.
 *
 * This is a service, rather than a global object, so that its cache lives and dies with the plugin.
 */
@Service(Service.Level.APP)
class MdnDocsService {
    companion object {
        val instance get() = service<MdnDocsService>()
    }

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
        return cache.asMap().computeIfAbsent(name) { attr ->
            try {
                val responseJson = HttpRequests.request(url)
                    .accept("application/json")
                    .connectTimeout(NETWORK_TIMEOUT_MS)
                    .readTimeout(NETWORK_TIMEOUT_MS)
                    .readString()

                val rootNode = objectMapper.readTree(responseJson)

                fun JsonNode.isProse() = path("type").asText() == "prose"
                fun JsonNode.id() = path("value").path("id").asText().takeIf { it != "null" }
                val body = rootNode.path("doc").path("body")
                val proseEntries = body.asSequence().filter { it.isProse() }

                // Use the "description" section, if present. Otherwise, just use the first section, which acts as a
                // short summary.
                val docHtml = (
                        proseEntries.firstOrNull { it.id() == "description" }
                            ?: proseEntries.firstOrNull()
                        )?.let { node -> node.path("value").path("content").asText().takeIf { it.isNotBlank() } }

                docHtml
            } catch (_: Exception) {
                null
            }
        }
    }
}
