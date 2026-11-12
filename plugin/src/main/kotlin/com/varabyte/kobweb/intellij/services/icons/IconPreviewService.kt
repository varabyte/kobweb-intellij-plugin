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

/** The result of looking up an icon's previews (see [IconPreviewService.IconSetPreviews.findAll]). */
sealed interface IconPreviewResult {
    /**
     * The icon's set hasn't loaded yet.
     *
     * You should listen to [IconPreviewService.ICON_SET_LOADED] to be told when it has.
     */
    data object NotLoaded : IconPreviewResult

    /** The icon's set has loaded, but it has no icon by this name. */
    data object NotFound : IconPreviewResult

    /** The icon in every style it's available in, in order of preference (so the first is its default style). */
    class Found(val previews: List<IconPreview>) : IconPreviewResult {
        init {
            require(previews.isNotEmpty()) { "Found must have at least one preview" }
        }
    }
}

/**
 * Previews of the icon that [call] renders, in every style it's available in, in order of preference (so the first is
 * its default style).
 */
class IconCallPreviews private constructor(val call: SilkIconCall, val all: List<IconPreview>) {
    /** The preview in the style [call] renders, i.e. the one it requests, or else its default style. */
    val bestFit: IconPreview =
        (call.styleArgument as? SilkIconCall.StyleArgument.Direct)
            ?.let { arg -> all.find { it.style == arg.style } }
            ?: all.first()

    companion object {
        /** Looks up previews of [call]'s icon, or returns null if there are none (e.g. until its icon set loads). */
        fun find(call: SilkIconCall): IconCallPreviews? {
            val result = IconPreviewService.instance.previewsFor(call.iconSet, call.iconSetVersion)
                .findAll(call.functionName)
            return (result as? IconPreviewResult.Found)?.let { IconCallPreviews(call, it.previews) }
        }
    }
}

/**
 * Starts loading [iconSet]'s previews in the background, so they're likely ready by the time they're needed.
 *
 * This is a convenience function for [previewsFor] essentially, but it is provided for readability.
 */
fun IconPreviewService.startLoading(iconSet: SilkIconSet, iconSetVersion: SemVer?) {
    previewsFor(iconSet, iconSetVersion)
}

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
 * is loaded in the background the first time its previews are requested (see [previewsFor]); once it's ready, open
 * editors are re-highlighted so their previews show up.
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
     * One icon set's previews, which load in the background. Get one from [previewsFor].
     */
    inner class IconSetPreviews internal constructor(private val iconSet: SilkIconSet, private val dataId: String) {
        /**
         * Looks up previews of the icon that Kobweb's [functionName] function renders (e.g. "FaHouse"), in every style
         * it's available in.
         *
         * If you already have a [SilkIconCall], use [IconCallPreviews.find] instead.
         */
        fun findAll(functionName: String): IconPreviewResult {
            val loaded = loadedIconSets[PreviewDataKey(iconSet, dataId)] ?: return IconPreviewResult.NotLoaded

            // An upstream set can reuse a deprecated name for a new icon, so check icons before renames
            val isIcon = loaded.styles.values.any { functionName in it }
            val currentName = if (isIcon) functionName else loaded.renames[functionName] ?: functionName
            val previews = iconSet.styles.mapNotNull { style ->
                loaded.styles[style.id]?.get(currentName)?.let { IconPreview(it, iconSet, style) }
            }
            return if (previews.isEmpty()) IconPreviewResult.NotFound else IconPreviewResult.Found(previews)
        }
    }

    /**
     * A non-blocking call to get previews for [iconSet], which start loading in the background if not already loaded.
     *
     * @param iconSetVersion The wrapped model version of the icon set.
     */
    fun previewsFor(iconSet: SilkIconSet, iconSetVersion: SemVer?): IconSetPreviews {
        val key = PreviewDataKey(iconSet, iconSet.previewId(iconSetVersion))
        requestLoad(key)
        return IconSetPreviews(iconSet, key.id)
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
