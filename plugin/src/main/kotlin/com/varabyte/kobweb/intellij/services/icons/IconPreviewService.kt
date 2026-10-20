package com.varabyte.kobweb.intellij.services.icons

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.HttpRequests
import com.varabyte.kobweb.intellij.iconpreviews.IconPreviewFormat
import com.varabyte.kobweb.intellij.iconpreviews.IconSet
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconCall
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconSet
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

class IconPreview(val shape: IconShape, val iconSet: SilkIconSet, val style: SilkIconStyle) {
    val description get() = "${iconSet.displayName}: ${shape.name} (${style.displayName})"
}

private const val DATA_URL = "https://raw.githubusercontent.com/varabyte/data/main/kobweb-intellij-plugin/icon-previews"
private const val NETWORK_TIMEOUT_MS = 5000

/**
 * Provides outlines for Kobweb's silk icons, for previewing them in the editor.
 *
 * Icon data is downloaded on demand from the varabyte/data repo and cached in the IDE's system directory. Each icon set
 * is loaded in the background the first time one of its icons is requested; until then, [findPreview] returns null,
 * and once it's ready, open editors are re-highlighted so their previews show up.
 *
 * The remote manifest is checked once per IDE session, and only files whose checksums changed are downloaded again.
 * When offline, previously cached files are used as is.
 */
@Service(Service.Level.APP)
class IconPreviewService(private val coroutineScope: CoroutineScope) {
    companion object {
        val instance get() = service<IconPreviewService>()
    }

    private class LoadedIconSet(
        // Style ID -> icon key -> shape
        val styles: Map<String, Map<String, IconShape>>,
        // Deprecated icon key -> replacement icon key
        val aliases: Map<String, String>,
    )

    private class ManifestEntry(val file: String, val sha256: String)

    private val logger = logger<IconPreviewService>()

    // For iterating on the data locally, e.g. `./gradlew :plugin:runIde -PlocalIconPreviews`, which points this at the
    // output of `:tools:icon-previews-generator:generate`. If set, the network and the cache are skipped entirely.
    private val localDataDir: Path? = System.getProperty("kobweb.iconPreviews.dir")?.let { Path.of(it) }
    private val cacheDir: Path = PathManager.getSystemDir().resolve("kobweb/icon-previews")

    // Icon set ID -> entry, or null if the remote manifest is unavailable (e.g. offline) or in a format we don't
    // support
    private val remoteManifest = coroutineScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) { fetchManifest() }

    private val requestedIconSets = ConcurrentHashMap.newKeySet<SilkIconSet>()
    private val loadedIconSets = ConcurrentHashMap<SilkIconSet, LoadedIconSet>()

    fun findPreview(call: SilkIconCall): IconPreview? {
        val loaded = loadedIconSets[call.iconSet]
        if (loaded == null) {
            requestLoad(call.iconSet)
            return null
        }

        val key = loaded.aliases[call.key] ?: call.key
        val candidateStyles = listOfNotNull(call.requestedStyle) + call.iconSet.styles
        return candidateStyles.firstNotNullOfOrNull { style ->
            loaded.styles[style.id]?.get(key)?.let { IconPreview(it, call.iconSet, style) }
        }
    }

    private fun requestLoad(iconSet: SilkIconSet) {
        // Only try once per session. If we fail (say, offline with nothing cached), we'll try again after a restart.
        if (!requestedIconSets.add(iconSet)) return

        coroutineScope.launch(Dispatchers.IO) {
            val file = localDataDir?.resolve("${iconSet.id}.bin")
                ?: findOrDownload(iconSet, remoteManifest.await())
                ?: return@launch
            val loaded = load(iconSet, file) ?: return@launch
            loadedIconSets[iconSet] = loaded
            ProjectManager.getInstance().openProjects
                .filter { !it.isDisposed }
                .forEach { DaemonCodeAnalyzer.getInstance(it).restart("Silk icon previews loaded") }
        }
    }

    /**
     * Returns the cached file for the icon set, downloading it first if it's missing or out of date.
     *
     * This blocks on file and network I/O, so it should only be called off the EDT (e.g. on [Dispatchers.IO]).
     *
     * @param manifest The remote manifest, or null if it's unavailable.
     */
    private fun findOrDownload(iconSet: SilkIconSet, manifest: Map<String, ManifestEntry>?): Path? {
        val cachedFile = cacheDir.resolve("${iconSet.id}.bin")
        val entry = manifest?.get(iconSet.id)
            // Remote data is unavailable, so use whatever we have. If the format changed, `load` will reject it.
            ?: return cachedFile.takeIf { it.exists() }

        return try {
            if (cachedFile.exists() && cachedFile.readBytes().sha256() == entry.sha256) return cachedFile

            val bytes = createRequest("$DATA_URL/${entry.file}").readBytes(null)
            check(bytes.sha256() == entry.sha256) { "Checksum mismatch for ${entry.file}" }
            cacheDir.createDirectories()
            val tempFile = Files.createTempFile(cacheDir, iconSet.id, ".tmp")
            try {
                tempFile.writeBytes(bytes)
                Files.move(tempFile, cachedFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                // Don't leave partial files behind if writing or moving failed (a no-op if the move succeeded)
                tempFile.deleteIfExists()
            }
            cachedFile
        } catch (e: Exception) {
            logger.info("Could not update icon previews for ${iconSet.id}", e)
            cachedFile.takeIf { it.exists() }
        }
    }

    private fun fetchManifest(): Map<String, ManifestEntry>? {
        return try {
            val root = ObjectMapper().readTree(createRequest("$DATA_URL/manifest.json").readString())
            val formatVersion = root.path("formatVersion").asInt()
            if (formatVersion != IconPreviewFormat.VERSION) {
                // Probably an older version of this plugin, while newer ones moved on to a new format
                logger.info("Unsupported icon preview format version $formatVersion")
                return null
            }
            root.path("sets").properties().associate { (id, set: JsonNode) ->
                id to ManifestEntry(file = set.path("file").asText(), sha256 = set.path("sha256").asText())
            }
        } catch (e: Exception) {
            logger.info("Could not fetch icon preview manifest", e)
            null
        }
    }

    private fun load(iconSet: SilkIconSet, file: Path): LoadedIconSet? {
        return try {
            index(file.inputStream().buffered().use { IconPreviewFormat.read(iconSet.id, it) })
        } catch (e: Exception) {
            logger.warn("Could not load icon previews from $file", e)
            null
        }
    }

    private fun index(data: IconSet) = LoadedIconSet(
        styles = data.styles.associate { style ->
            style.id to style.icons.associateBy { SilkIconCall.keyFor(it.name) }
        },
        aliases = data.aliases.entries.associate { (from, to) -> SilkIconCall.keyFor(from) to SilkIconCall.keyFor(to) },
    )

    /**
     * Use [data] for [iconSet] instead of downloading it, until [parentDisposable] is disposed.
     *
     * This lets tests provide icons synchronously, without touching the network.
     */
    @TestOnly
    fun useIconSetForTests(iconSet: SilkIconSet, data: IconSet, parentDisposable: Disposable) {
        requestedIconSets.add(iconSet)
        loadedIconSets[iconSet] = index(data)
        Disposer.register(parentDisposable) {
            loadedIconSets.remove(iconSet)
            requestedIconSets.remove(iconSet)
        }
    }
}

private fun createRequest(url: String) = HttpRequests.request(url)
    .connectTimeout(NETWORK_TIMEOUT_MS)
    .readTimeout(NETWORK_TIMEOUT_MS)

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHexString()
