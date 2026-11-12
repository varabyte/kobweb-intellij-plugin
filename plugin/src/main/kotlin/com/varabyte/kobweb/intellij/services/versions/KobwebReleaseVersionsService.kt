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
import org.gradle.util.GradleVersion
import org.toml.lang.psi.ext.kind
import java.io.IOException
import java.util.Properties
import kotlin.time.Duration

private const val NETWORK_TIMEOUT_MS = 5000
private const val LATEST_RELEASE_URL = "$KOBWEB_REPO_URL/releases/latest"

/**
 * The versions that a Kobweb release was built with.
 *
 * @property kobwebVersion The release's own version, from its tag, e.g. `"0.25.1"` for `v0.25.1`.
 * @property catalogVersions The `[versions]` table of Kobweb's version catalog, e.g. `"kotlin"` to `"2.4.10"`.
 * @property gradleVersion The Gradle version of Kobweb's wrapper, e.g. `"9.7.0"`, or null if it couldn't be read.
 */
data class KobwebReleaseVersions(
    val kobwebVersion: String,
    val catalogVersions: Map<String, String>,
    val gradleVersion: String?,
)

/**
 * Fetches the versions that the latest Kobweb release uses (Kobweb itself, Kotlin, Compose, Gradle, etc.), so new
 * modules and projects get up-to-date versions even if this plugin hasn't been updated since that release.
 *
 * The versions come from Kobweb's own version catalog and Gradle wrapper at the release's tag, so they always match the
 * versions Kobweb was built and tested with.
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

        /**
         * The Gradle version of a `gradle-wrapper.properties` file's distribution, e.g. `"9.7.0"` for
         * `distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.0-bin.zip`, or null if it has none.
         */
        internal fun readGradleVersion(wrapperPropertiesText: String): String? {
            val properties = Properties().apply { load(wrapperPropertiesText.reader()) }
            val distributionUrl = properties.getProperty("distributionUrl") ?: return null
            val version = Regex("""/gradle-(.+)-(?:bin|all)\.zip$""").find(distributionUrl)?.groupValues?.get(1)
            return version?.takeIf { runCatching { GradleVersion.version(it) }.isSuccess }
        }
    }

    private val logger = logger<KobwebReleaseVersionsService>()

    /** The latest release's versions, once a fetch succeeds. */
    @Volatile
    private var latestVersions: KobwebReleaseVersions? = null

    /** The current or last fetch. A failed one (which leaves [latestVersions] null) is retried by [startFetch]. */
    @Volatile
    private var fetch: Deferred<KobwebReleaseVersions?>? = null

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
    fun latestVersionsIfFetched(): KobwebReleaseVersions? = latestVersions

    /**
     * The latest release's versions, waiting up to [timeout] for them to be fetched. Call [prefetch] earlier to wait
     * less.
     *
     * @return null if the fetch failed or timed out.
     */
    suspend fun awaitLatestVersions(timeout: Duration): KobwebReleaseVersions? {
        val fetch = startFetch() ?: return latestVersions
        return withTimeoutOrNull(timeout) { fetch.await() }
    }

    /** Starts a fetch unless the versions are fetched or being fetched, returning the fetch in progress, if any. */
    private fun startFetch(): Deferred<KobwebReleaseVersions?>? {
        synchronized(this) {
            if (latestVersions != null) return null
            if (fetch?.isActive != true) {
                fetch = coroutineScope.async(Dispatchers.IO) { fetchLatestVersions()?.also { latestVersions = it } }
            }
            return fetch
        }
    }

    private fun fetchLatestVersions(): KobwebReleaseVersions? {
        val tag = fetchLatestReleaseTag() ?: return null
        val catalogUrl = "$KOBWEB_RAW_FILES_URL_BASE/$tag/gradle/libs.versions.toml"
        val catalogText = RemoteFile(catalogUrl).download()?.decodeToString() ?: return null
        val catalogVersions = readVersions(ProjectManager.getInstance().defaultProject, catalogText)
            .takeIf { it.isNotEmpty() }
            ?: run {
                logger.warn("Found no versions in $catalogUrl")
                return null
            }
        return KobwebReleaseVersions(
            kobwebVersion = tag.removePrefix("v"),
            catalogVersions,
            gradleVersion = fetchGradleVersion(tag),
        )
    }

    /**
     * The Gradle version of the wrapper at [tag], or null if it can't be read.
     *
     * We use Kobweb's own Gradle version rather than Gradle's latest release, since Kobweb keeps it reasonably up to
     * date and its builds (including its playground project) are tested with it. Gradle's latest release, by contrast,
     * isn't always supported by the Kotlin Gradle plugin yet.
     */
    private fun fetchGradleVersion(tag: String): String? {
        val wrapperUrl = "$KOBWEB_RAW_FILES_URL_BASE/$tag/gradle/wrapper/gradle-wrapper.properties"
        val wrapperText = RemoteFile(wrapperUrl).download()?.decodeToString() ?: return null
        return readGradleVersion(wrapperText)
            .also { if (it == null) logger.warn("Found no Gradle version in $wrapperUrl") }
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
