package com.varabyte.kobweb.intellij.services.templates

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.childrenOfType
import com.intellij.util.text.SemVer
import com.varabyte.kobweb.intellij.util.github.GitHubRepo
import com.varabyte.kobweb.intellij.util.kobweb.Kobweb
import com.varabyte.kobweb.intellij.util.kobweb.VarabyteData
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
import java.util.Properties
import kotlin.time.Duration

// Which release to use is read from a file in the varabyte/data repo, which is updated only after a new release has
// been checked against this plugin's module templates. That way, a Kobweb release can't break the templates of
// already-installed plugins, and a bad update can be rolled back without a plugin release.
private val VERIFIED_VERSION_URL =
    GitHubRepo.VarabyteData.rawFileUrl("kobweb-intellij-plugin/module-templates/verified-kobweb-version.txt")

// The key of Kobweb's own version in Kobweb's version catalog
private const val KOBWEB_CATALOG_VERSION_KEY = "kobweb-framework"

/**
 * The versions that a Kobweb release was built with.
 *
 * @property kobwebVersion The release's own version, e.g. `"0.25.1"`.
 * @property catalogVersions The `[versions]` table of Kobweb's version catalog, e.g. `"kotlin"` to `"2.4.10"`.
 * @property gradleVersion The Gradle version of Kobweb's wrapper, e.g. `"9.7.0"`, or null if it couldn't be read.
 */
data class KobwebReleaseVersions(
    val kobwebVersion: String,
    val catalogVersions: Map<String, String>,
    val gradleVersion: String?,
)

/** Which Kobweb version [ModuleTemplateVersionsService] fetches the versions of. */
private sealed interface KobwebVersionSource {
    /** The latest release that's been verified to work with this plugin's module templates. */
    data object Verified : KobwebVersionSource

    /** Whatever version is on a branch of the Kobweb repo, e.g. `dev`, for trying out an unreleased version. */
    data class Branch(val name: String) : KobwebVersionSource
}

/**
 * Fetches the versions that new modules from this plugin's module templates use (Kobweb itself, Kotlin, Compose,
 * Gradle, etc.), so they get up-to-date versions even if this plugin hasn't been updated since the latest Kobweb
 * release.
 *
 * The versions come from Kobweb's own version catalog and Gradle wrapper at the release's tag, so they always match the
 * versions Kobweb was built and tested with.
 */
@Service(Service.Level.APP)
class ModuleTemplateVersionsService(private val coroutineScope: CoroutineScope) {
    companion object {
        val instance get() = service<ModuleTemplateVersionsService>()

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

    private val logger = logger<ModuleTemplateVersionsService>()

    // Normally the latest verified release, but it can be set to a branch for checking the module templates against an
    // unreleased Kobweb, e.g. `./gradlew :plugin:runIde -Pkobweb.moduleTemplates.fetchVersionFromBranch=dev`
    private val source: KobwebVersionSource =
        System.getProperty("kobweb.moduleTemplates.fetchVersionFromBranch")
            ?.let { KobwebVersionSource.Branch(it) }
            ?: KobwebVersionSource.Verified

    /** The versions, once a fetch succeeds. */
    @Volatile
    private var fetchedVersions: KobwebReleaseVersions? = null

    /** The current or last fetch. A failed one (which leaves [fetchedVersions] null) is retried by [startFetch]. */
    @Volatile
    private var fetch: Deferred<KobwebReleaseVersions?>? = null

    /** Starts fetching the versions in the background, unless they're fetched or being fetched. */
    fun prefetch() {
        @Suppress("DeferredResultUnused") // We are starting a fetch in the background to collect later
        startFetch()
    }

    /**
     * The versions, if they've been fetched already.
     *
     * If this returns null, callers can wait for the fetch with [awaitVersions], or use the versions bundled with this
     * plugin.
     */
    fun versionsIfFetched(): KobwebReleaseVersions? = fetchedVersions

    /**
     * The versions, waiting up to [timeout] for them to be fetched. Call [prefetch] earlier to wait less.
     *
     * @return null if the fetch failed or timed out.
     */
    suspend fun awaitVersions(timeout: Duration): KobwebReleaseVersions? {
        val fetch = startFetch() ?: return fetchedVersions
        return withTimeoutOrNull(timeout) { fetch.await() }
    }

    /** Starts a fetch unless the versions are fetched or being fetched, returning the fetch in progress, if any. */
    private fun startFetch(): Deferred<KobwebReleaseVersions?>? {
        synchronized(this) {
            if (fetchedVersions != null) return null
            if (fetch?.isActive != true) {
                fetch = coroutineScope.async(Dispatchers.IO) { fetchVersions()?.also { fetchedVersions = it } }
            }
            return fetch
        }
    }

    private fun fetchVersions(): KobwebReleaseVersions? {
        // A branch's Kobweb version is only known once its catalog is read
        val (ref, verifiedVersion) = when (source) {
            KobwebVersionSource.Verified -> {
                val version = fetchVerifiedVersion() ?: return null
                "v$version" to version
            }
            is KobwebVersionSource.Branch -> source.name to null
        }
        val catalogUrl = GitHubRepo.Kobweb.rawFileUrl("gradle/libs.versions.toml", ref = ref)
        val catalogText = RemoteFile(catalogUrl).download()?.decodeToString() ?: return null
        val catalogVersions = readVersions(ProjectManager.getInstance().defaultProject, catalogText)
            .takeIf { it.isNotEmpty() }
            ?: run {
                logger.warn("Found no versions in $catalogUrl")
                return null
            }
        val kobwebVersion = verifiedVersion
            ?: catalogVersions[KOBWEB_CATALOG_VERSION_KEY]
            ?: run {
                logger.warn("Found no $KOBWEB_CATALOG_VERSION_KEY version in $catalogUrl")
                return null
            }
        return KobwebReleaseVersions(
            kobwebVersion,
            catalogVersions,
            gradleVersion = fetchGradleVersion(ref),
        )
    }

    /**
     * The Gradle version of the wrapper at [ref], or null if it can't be read.
     *
     * We use Kobweb's own Gradle version rather than Gradle's latest release, since Kobweb keeps it reasonably up to
     * date and its builds (including its playground project) are tested with it. Gradle's latest release, by contrast,
     * isn't always supported by the Kotlin Gradle plugin yet.
     */
    private fun fetchGradleVersion(ref: String): String? {
        val wrapperUrl = GitHubRepo.Kobweb.rawFileUrl("gradle/wrapper/gradle-wrapper.properties", ref = ref)
        val wrapperText = RemoteFile(wrapperUrl).download()?.decodeToString() ?: return null
        return readGradleVersion(wrapperText)
            .also { if (it == null) logger.warn("Found no Gradle version in $wrapperUrl") }
    }

    /** The latest Kobweb version that's been verified to work with this plugin's module templates. */
    private fun fetchVerifiedVersion(): String? {
        val text = RemoteFile(VERIFIED_VERSION_URL).download()?.decodeToString() ?: return null
        /** The Kobweb version in a verified version file's text, e.g. `"0.25.1"`, or null if it isn't a version. */
        fun String.readVerifiedVersion(): String? = trim().takeIf { SemVer.parseFromText(it) != null }
        return text.readVerifiedVersion()
            .also { if (it == null) logger.warn("Unexpected version format in $VERIFIED_VERSION_URL: $text") }
    }
}
