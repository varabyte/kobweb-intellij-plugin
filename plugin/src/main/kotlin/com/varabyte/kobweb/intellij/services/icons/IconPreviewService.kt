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
import com.varabyte.kobweb.intellij.iconpreviews.IconPreviewFormat
import com.varabyte.kobweb.intellij.iconpreviews.IconSet
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import com.varabyte.kobweb.intellij.util.io.writeBytesAtomically
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconCall
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconSet
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconStyle
import com.varabyte.kobweb.intellij.util.network.RemoteFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.readBytes

class IconPreview(val shape: IconShape, val iconSet: SilkIconSet, val style: SilkIconStyle)

/**
 * Returns the preview of the icon [call] renders, i.e. in the style it requests, or else its default style.
 *
 * The receiver is expected to be the result of [IconPreviewService.findAllPreviews] for the same call.
 */
fun List<IconPreview>.findRenderedBy(call: SilkIconCall): IconPreview? =
    (call.styleArgument as? SilkIconCall.StyleArgument.Direct)?.let { arg -> find { it.style == arg.style } }
        ?: firstOrNull()

// The data lives in a folder per format version, so older versions of this plugin can keep using data in the format
// they understand
private const val DATA_URL =
    "https://raw.githubusercontent.com/varabyte/data/main/kobweb-intellij-plugin/icon-previews/v${IconPreviewFormat.VERSION}"

/**
 * Provides outlines for Kobweb's silk icons, for previewing them in the editor.
 *
 * Icon data is downloaded on demand from the varabyte/data repo and cached in the IDE's system directory. Each icon set
 * is loaded in the background the first time one of its icons is requested; until then, [findAllPreviews] returns
 * nothing, and once it's ready, open editors are re-highlighted so their previews show up.
 *
 * The remote manifest is checked once per IDE session, and only files whose checksums changed are downloaded again.
 * When offline, previously cached files are used while downloads are retried in the background.
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
    private val cacheDir: Path =
        PathManager.getSystemDir().resolve("kobweb/icon-previews/v${IconPreviewFormat.VERSION}")

    // Icon set ID -> entry, or null if the remote manifest is broken. Waits until the manifest can be downloaded (e.g.
    // while offline).
    private val remoteManifest = coroutineScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) { fetchManifest() }

    private val requestedIconSets = ConcurrentHashMap.newKeySet<SilkIconSet>()
    private val loadedIconSets = ConcurrentHashMap<SilkIconSet, LoadedIconSet>()

    /**
     * Returns a preview of [call]'s icon in every style it's available in, in order of preference (so the first is the
     * style used when the call doesn't request one).
     *
     * This is empty if the icon is unknown, or if its icon set hasn't loaded yet. Use [findRenderedBy] on the result to
     * get the preview in the style the call actually renders.
     */
    fun findAllPreviews(call: SilkIconCall): List<IconPreview> {
        val loaded = loadedIconSets[call.iconSet]
        if (loaded == null) {
            requestLoad(call.iconSet)
            return emptyList()
        }

        val key = loaded.aliases[call.key] ?: call.key
        return call.iconSet.styles.mapNotNull { style ->
            loaded.styles[style.id]?.get(key)?.let { IconPreview(it, call.iconSet, style) }
        }
    }

    private fun requestLoad(iconSet: SilkIconSet) {
        // Only load once per session (download failures are retried inside the load itself)
        if (!requestedIconSets.add(iconSet)) return

        coroutineScope.launch(Dispatchers.IO) {
            if (localDataDir != null) {
                load(iconSet, localDataDir.resolve("${iconSet.id}.bin"))?.let { publish(iconSet, it) }
                return@launch
            }

            // Use the cached copy right away, as the network may be slow (or unavailable). If the format changed,
            // `load` will reject it.
            val cachedFile = cacheDir.resolve("${iconSet.id}.bin")
            if (cachedFile.exists()) load(iconSet, cachedFile)?.let { publish(iconSet, it) }
            if (updateCache(iconSet, cachedFile)) load(iconSet, cachedFile)?.let { publish(iconSet, it) }
        }
    }

    private fun publish(iconSet: SilkIconSet, loaded: LoadedIconSet) {
        loadedIconSets[iconSet] = loaded
        ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .forEach { DaemonCodeAnalyzer.getInstance(it).restart("Silk icon previews loaded") }
    }

    /**
     * Downloads the icon set into [cachedFile] if it's missing or out of date, returning true if it did.
     *
     * This waits until the remote manifest and the icon set can be downloaded, e.g. while offline.
     */
    private suspend fun updateCache(iconSet: SilkIconSet, cachedFile: Path): Boolean {
        val entry = remoteManifest.await()?.get(iconSet.id) ?: return false
        return try {
            if (cachedFile.exists() && cachedFile.readBytes().sha256() == entry.sha256) return false

            val bytes = RemoteFile("$DATA_URL/${entry.file}").downloadWithRetries { it.sha256() == entry.sha256 }
                // The manifest is probably out of date, so keep using the cached copy (if any)
                ?: return false
            cachedFile.writeBytesAtomically(bytes)
            true
        } catch (e: IOException) {
            logger.info("Could not update icon previews for ${iconSet.id}", e)
            false
        }
    }

    private suspend fun fetchManifest(): Map<String, ManifestEntry>? {
        val manifestBytes = RemoteFile("$DATA_URL/manifest.json").downloadWithRetries()
        return try {
            val root = ObjectMapper().readTree(manifestBytes)
            val formatVersion = root.path("formatVersion").asInt()
            if (formatVersion != IconPreviewFormat.VERSION) {
                // This shouldn't happen unless we (the Kobweb maintainers) screwed up
                logger.warn("Unexpected icon preview format version $formatVersion in $DATA_URL")
                return null
            }
            root.path("sets").properties().associate { (id, set: JsonNode) ->
                id to ManifestEntry(file = set.path("file").asText(), sha256 = set.path("sha256").asText())
            }
        } catch (e: Exception) {
            logger.warn("Could not parse icon preview manifest", e)
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

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHexString()
