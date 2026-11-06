package com.varabyte.kobweb.intellij.services.versions

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.childrenOfType
import com.intellij.util.io.HttpRequests
import com.varabyte.kobweb.intellij.util.kobweb.KOBWEB_RAW_FILES_URL_BASE
import com.varabyte.kobweb.intellij.util.kobweb.KOBWEB_REPO_URL
import com.varabyte.kobweb.intellij.util.network.RemoteFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import java.io.IOException
import kotlin.time.Duration

private const val NETWORK_TIMEOUT_MS = 5000
private const val LATEST_RELEASE_URL = "$KOBWEB_REPO_URL/releases/latest"

/**
 * Fetches the versions that the latest Kobweb release uses (Kobweb itself, Kotlin, Compose, etc.), so new modules get
 * up-to-date versions even if this plugin hasn't been updated since that release.
 *
 * The versions come from Kobweb's own version catalog at the release's tag, so they always match the versions Kobweb
 * was built and tested with.
 */
@Service(Service.Level.APP)
class KobwebReleaseVersionsService(private val coroutineScope: CoroutineScope) {
    companion object {
        val instance get() = service<KobwebReleaseVersionsService>()

        /**
         * The release tag that a GitHub release URL ends with, e.g. `v0.25.1`.
         *
         * Returns null if the URL isn't a release page (e.g. a proxy redirected the request to its own page) or if the
         * tag isn't in Kobweb's `vX.Y.Z` format, which shouldn't happen.
         */
        internal fun findReleaseTag(url: String): String? =
            url.substringAfterLast("/releases/tag/", missingDelimiterValue = "")
                .takeIf { Regex("""v\d+\.\d+\.\d+""").matches(it) }

        /** The `[versions]` table of a version catalog, e.g. `"kobweb"` to `"0.25.1"`. */
        internal fun readVersions(project: Project, catalogText: String): Map<String, String> =
            ReadAction.compute<Map<String, String>, Throwable> {
                val file = PsiFileFactory.getInstance(project)
                    .createFileFromText("libs.versions.toml", TomlLanguage, catalogText) as TomlFile
                val versionsTable = file.childrenOfType<TomlTable>().firstOrNull { it.header.key?.text == "versions" }
                versionsTable?.entries.orEmpty()
                    .mapNotNull { entry ->
                        val value = (entry.value as? TomlLiteral)?.kind as? TomlLiteralKind.String
                        value?.value?.let { entry.key.text to it }
                    }
                    .toMap()
            }
    }

    private val logger = logger<KobwebReleaseVersionsService>()

    /** The latest release's versions, once a fetch succeeds. */
    @Volatile
    private var latestVersions: Map<String, String>? = null

    /** The current or last fetch. A failed one (which leaves [latestVersions] null) is retried by [startFetch]. */
    @Volatile
    private var fetch: Deferred<Map<String, String>?>? = null

    /** Starts fetching the latest release's versions in the background, unless they're fetched or being fetched. */
    fun prefetch() {
        @Suppress("DeferredResultUnused") // We are starting a fetch in the background to collect later
        startFetch()
    }

    /**
     * The latest release's versions, if they've been fetched already.
     *
     * If this returns null, callers can wait for the fetch with [awaitLatestVersions], or use the versions bundled with
     * this plugin.
     */
    fun latestVersionsIfFetched(): Map<String, String>? = latestVersions

    /**
     * The latest release's versions, waiting up to [timeout] for them to be fetched. Call [prefetch] earlier to wait
     * less.
     *
     * @return null if the fetch failed or timed out.
     */
    suspend fun awaitLatestVersions(timeout: Duration): Map<String, String>? {
        val fetch = startFetch() ?: return latestVersions
        return withTimeoutOrNull(timeout) { fetch.await() }
    }

    /** Starts a fetch unless the versions are fetched or being fetched, returning the fetch in progress, if any. */
    private fun startFetch(): Deferred<Map<String, String>?>? {
        synchronized(this) {
            if (latestVersions != null) return null
            if (fetch?.isActive != true) {
                fetch = coroutineScope.async(Dispatchers.IO) { fetchLatestVersions()?.also { latestVersions = it } }
            }
            return fetch
        }
    }

    private fun fetchLatestVersions(): Map<String, String>? {
        val tag = fetchLatestReleaseTag() ?: return null
        val catalogUrl = "$KOBWEB_RAW_FILES_URL_BASE/$tag/gradle/libs.versions.toml"
        val catalogText = RemoteFile(catalogUrl).download()?.decodeToString() ?: return null
        return readVersions(ProjectManager.getInstance().defaultProject, catalogText)
            .takeIf { it.isNotEmpty() }
            .also { if (it == null) logger.warn("Found no versions in $catalogUrl") }
    }

    /** Reads the latest release's tag from where GitHub redirects its "latest release" page to. */
    private fun fetchLatestReleaseTag(): String? {
        val finalUrl = try {
            HttpRequests.head(LATEST_RELEASE_URL)
                .connectTimeout(NETWORK_TIMEOUT_MS)
                .readTimeout(NETWORK_TIMEOUT_MS)
                .connect { request ->
                    request.connection // Follows the redirects
                    request.url
                }
        } catch (e: IOException) {
            logger.info("Could not find Kobweb's latest release: ${e.message}")
            return null
        }
        return findReleaseTag(finalUrl)
            .also { if (it == null) logger.warn("Unexpected latest release URL format: $finalUrl") }
    }
}
