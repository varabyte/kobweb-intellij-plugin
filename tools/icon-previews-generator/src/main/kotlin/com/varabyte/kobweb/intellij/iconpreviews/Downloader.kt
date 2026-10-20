package com.varabyte.kobweb.intellij.iconpreviews

import java.net.http.HttpResponse
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText

/**
 * Fetches remote files, caching them on disk by URL.
 *
 * Callers should only pass in URLs whose content never changes (e.g. ones pinned to a commit SHA or a release version),
 * since a cached file is never refreshed.
 */
class Downloader(private val cacheDir: Path) {
    fun fetch(url: String): Path {
        // Cached files are named "<url hash>-<sanitized last URL segment>", e.g. for a URL ending in
        // "MaterialSymbolsOutlined%5BFILL,GRAD,opsz,wght%5D.ttf":
        //   00a6df5e71b9-MaterialSymbolsOutlined_5BFILL_GRAD_opsz_wght_5D.ttf
        // The hash keeps entries unique (different URLs can end in the same name, e.g. one file at different commits).
        // The rest is only there so humans can tell cached files apart, with characters that would be awkward or
        // unsafe in a filename (like '%', ',', '?', or ':') replaced by underscores.
        val urlHash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).toHexString().take(12)
        val fileName = url.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
        val cached = cacheDir.resolve("$urlHash-$fileName")
        if (!cached.exists()) {
            cacheDir.createDirectories()
            println("Downloading $url")
            val tmp = cacheDir.resolve("${cached.fileName}.tmp")
            val response = Http.client.send(Http.newRequest(url).build(), HttpResponse.BodyHandlers.ofFile(tmp))
            check(response.statusCode() == 200) { "GET $url failed with HTTP ${response.statusCode()}" }
            tmp.moveTo(cached, overwrite = true)
        }
        return cached
    }
}

fun Downloader.fetchText(url: String): String = fetch(url).readText()
