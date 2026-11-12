package com.varabyte.kobweb.intellij.services.icons

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.Topic
import com.intellij.util.text.SemVer
import com.varabyte.kobweb.intellij.iconpreviews.IconPreviewFormat
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import com.varabyte.kobweb.intellij.iconpreviews.PreviewIconSet
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

/**
 * Returns a preview of [call]'s icon in every style it's available in (see [IconPreviewService.findAllPreviews]).
 */
fun IconPreviewService.findAllPreviews(call: SilkIconCall): List<IconPreview> =
    findAllPreviews(call.iconSet, call.iconSetVersion, call.functionName)

/** Notified when [IconPreviewService] finishes loading an icon set's previews. */
fun interface IconPreviewsListener {
    /** Called on a background thread. */
    fun iconSetLoaded()
}

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

        @Topic.AppLevel
        val ICON_SET_LOADED = Topic(IconPreviewsListener::class.java)
    }

    private class LoadedIconSet(
        // Style ID -> function name -> shape
        val styles: Map<String, Map<String, IconShape>>,
        // Renamed icon's old function name -> its current function name
        val renames: Map<String, String>,
    )

    private class ManifestEntry(val file: String, val sha256: String)

    /**
     * Which preview data to load for an icon set, since a set can have different data per version.
     *
     * @property id The data's ID (see [SilkIconSet.previewId]), e.g. "fa7".
     */
    private data class PreviewDataKey(val iconSet: SilkIconSet, val id: String)

    private val logger = logger<IconPreviewService>()

    // For iterating on the data locally, e.g. `./gradlew :plugin:runIde -PlocalIconPreviews`, which points this at the
    // output of `:tools:icon-previews-generator:generate`. If set, the network and the cache are skipped entirely.
    private val localDataDir: Path? = System.getProperty("kobweb.iconPreviews.dir")?.let { Path.of(it) }
    private val cacheDir: Path =
        PathManager.getSystemDir().resolve("kobweb/icon-previews/v${IconPreviewFormat.VERSION}")

    // Icon set ID -> entry, or null if the remote manifest is broken. Waits until the manifest can be downloaded (e.g.
    // while offline).
    private val remoteManifest = coroutineScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) { fetchManifest() }

    private val requestedIconSets = ConcurrentHashMap.newKeySet<PreviewDataKey>()
    private val loadedIconSets = ConcurrentHashMap<PreviewDataKey, LoadedIconSet>()

    /**
     * Returns a preview of the icon that Kobweb's [functionName] function renders (e.g. "FaHouse"), in every style it's
     * available in, in order of preference (so the first is the style used when a call doesn't request one).
     *
     * This is empty if the icon is unknown, or if its icon set hasn't loaded yet. Use [findRenderedBy] on the result to
     * get the preview in the style the call actually renders.
     *
     * To find out when an icon set finishes loading (e.g. to repaint UI that got empty results), listen for
     * [ICON_SET_LOADED].
     *
     * @param iconSetVersion The upstream version of the icon set (see [SilkIconCall.iconSetVersion]), if known.
     */
    fun findAllPreviews(iconSet: SilkIconSet, iconSetVersion: SemVer?, functionName: String): List<IconPreview> {
        val key = PreviewDataKey(iconSet, iconSet.previewId(iconSetVersion))
        val loaded = loadedIconSets[key]
        if (loaded == null) {
            requestLoad(key)
            return emptyList()
        }

        // An upstream set can reuse a deprecated name for a new icon, so check icons before renames
        val isIcon = loaded.styles.values.any { functionName in it }
        val currentName = if (isIcon) functionName else loaded.renames[functionName] ?: functionName
        return iconSet.styles.mapNotNull { style ->
            loaded.styles[style.id]?.get(currentName)?.let { IconPreview(it, iconSet, style) }
        }
    }

    private fun requestLoad(key: PreviewDataKey) {
        // Only load once per session (download failures are retried inside the load itself)
        if (!requestedIconSets.add(key)) return

        coroutineScope.launch(Dispatchers.IO) {
            if (localDataDir != null) {
                load(key, localDataDir.resolve("${key.id}.bin"))?.let { publish(key, it) }
                return@launch
            }

            // Use the cached copy right away, as the network may be slow (or unavailable). If the format changed,
            // `load` will reject it.
            val cachedFile = cacheDir.resolve("${key.id}.bin")
            if (cachedFile.exists()) load(key, cachedFile)?.let { publish(key, it) }
            if (updateCache(key, cachedFile)) load(key, cachedFile)?.let { publish(key, it) }
        }
    }

    private fun publish(key: PreviewDataKey, loaded: LoadedIconSet) {
        loadedIconSets[key] = loaded
        ApplicationManager.getApplication().messageBus.syncPublisher(ICON_SET_LOADED).iconSetLoaded()
        ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .forEach { DaemonCodeAnalyzer.getInstance(it).restart("Silk icon previews loaded") }
    }

    /**
     * Downloads the data for [key] into [cachedFile] if it's missing or out of date, returning true if it did.
     *
     * This waits until the remote manifest and the data can be downloaded, e.g. while offline.
     */
    private suspend fun updateCache(key: PreviewDataKey, cachedFile: Path): Boolean {
        val entry = remoteManifest.await()?.get(key.id) ?: return false
        return try {
            if (cachedFile.exists() && cachedFile.readBytes().sha256() == entry.sha256) return false

            val bytes = RemoteFile("$DATA_URL/${entry.file}").downloadWithRetries { it.sha256() == entry.sha256 }
                // The manifest is probably out of date, so keep using the cached copy (if any)
                ?: return false
            cachedFile.writeBytesAtomically(bytes)
            true
        } catch (e: IOException) {
            logger.info("Could not update icon previews for ${key.id}", e)
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

    private fun load(key: PreviewDataKey, file: Path): LoadedIconSet? {
        return try {
            index(key.iconSet, file.inputStream().buffered().use { IconPreviewFormat.read(key.id, it) })
        } catch (e: Exception) {
            logger.warn("Could not load icon previews from $file", e)
            null
        }
    }

    /** Indexes [data] by the names of the functions Kobweb generates for its icons, which is how calls find them. */
    private fun index(iconSet: SilkIconSet, data: PreviewIconSet): LoadedIconSet {
        val namer = iconSet.wrapped.methodNamer
        return LoadedIconSet(
            styles = data.styles.associate { style -> style.id to style.icons.associateBy { namer.nameFor(it.name) } },
            renames = data.renames.entries.associate { (from, to) -> namer.nameFor(from) to namer.nameFor(to) },
        )
    }

    /**
     * Use [data] for [iconSet] instead of downloading it, until [parentDisposable] is disposed.
     *
     * The data's ID says which version of the set it's for (see `SilkIconSet.previewId`), e.g. "fa7".
     *
     * This lets tests provide icons synchronously, without touching the network.
     */
    @TestOnly
    fun useIconSetForTests(iconSet: SilkIconSet, data: PreviewIconSet, parentDisposable: Disposable) {
        val key = PreviewDataKey(iconSet, data.id)
        requestedIconSets.add(key)
        loadedIconSets[key] = index(iconSet, data)
        Disposer.register(parentDisposable) {
            loadedIconSets.remove(key)
            requestedIconSets.remove(key)
        }
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHexString()
