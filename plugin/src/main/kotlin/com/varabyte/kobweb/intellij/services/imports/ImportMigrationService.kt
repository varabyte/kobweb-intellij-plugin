package com.varabyte.kobweb.intellij.services.imports

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.varabyte.kobweb.intellij.util.io.writeBytesAtomically
import com.varabyte.kobweb.intellij.util.kobweb.imports.ImportMigrations
import com.varabyte.kobweb.intellij.util.network.RemoteFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.readText

// Bump this (and publish the file under a new version folder) if the format ever changes in a way that older versions
// of this plugin can't handle, so that they can keep using the old file
private const val FORMAT_VERSION = 1
private const val DATA_URL =
    "https://raw.githubusercontent.com/varabyte/data/main/kobweb-intellij-plugin/import-migrations/v$FORMAT_VERSION/migrations.txt"

/**
 * Provides the list of declarations that have moved in Kobweb and related libraries, so that imports still using their
 * old locations can be updated.
 *
 * The list is downloaded from the varabyte/data repo once per IDE session, the first time it's requested, and cached in
 * the IDE's system directory. Until then, [migrations] is empty (or, if available, holds what was cached last time),
 * and once it's ready, open editors are re-highlighted. When offline, the cached list is used while the download is
 * retried in the background.
 */
@Service(Service.Level.APP)
class ImportMigrationService(private val coroutineScope: CoroutineScope) {
    companion object {
        val instance get() = service<ImportMigrationService>()
    }

    private val logger = logger<ImportMigrationService>()

    // For trying out changes to the file before publishing them, e.g.
    // `./gradlew :plugin:runIde -PlocalImportMigrations=/path/to/migrations.txt`. If set, the network and the cache are
    // skipped entirely.
    private val localFile: Path? = System.getProperty("kobweb.importMigrations.file")?.let { Path.of(it) }
    private val remoteFile = RemoteFile(DATA_URL)
    private val cachedFile: Path =
        PathManager.getSystemDir().resolve("kobweb/import-migrations/v$FORMAT_VERSION/migrations.txt")

    private val loadRequested = AtomicBoolean(false)

    @Volatile
    private var loadedMigrations = ImportMigrations.Empty

    val migrations: ImportMigrations
        get() {
            requestLoad()
            return loadedMigrations
        }

    private fun requestLoad() {
        // Only load once per session (download failures are retried inside the load itself)
        if (!loadRequested.compareAndSet(false, true)) return

        coroutineScope.launch(Dispatchers.IO) {
            if (localFile != null) {
                readText(localFile)?.let { parse(it, source = localFile.toString()) }?.let(::publish)
                return@launch
            }

            // Use the cached copy right away, as the network may be slow (or unavailable)
            val cachedText = readText(cachedFile)
            cachedText?.let { parse(it, source = cachedFile.toString()) }?.let(::publish)

            val downloadedBytes = remoteFile.downloadWithRetries()
            val downloadedText = downloadedBytes.decodeToString()
            if (downloadedText == cachedText) return@launch
            // If the published file is broken, keep using the cached copy (if any) rather than caching the broken one.
            // Retrying won't help here, but a fixed file will be picked up after a restart.
            val downloaded = parse(downloadedText, source = DATA_URL) ?: return@launch
            publish(downloaded)
            try {
                cachedFile.writeBytesAtomically(downloadedBytes)
            } catch (e: IOException) {
                logger.info("Could not cache import migrations", e)
            }
        }
    }

    private fun parse(text: String, source: String): ImportMigrations? {
        return try {
            ImportMigrations.parse(text)
        } catch (e: IllegalArgumentException) {
            logger.warn("Could not parse import migrations from $source", e)
            null
        }
    }

    private fun publish(migrations: ImportMigrations) {
        loadedMigrations = migrations
        ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .forEach { DaemonCodeAnalyzer.getInstance(it).restart("Kobweb import migrations loaded") }
    }

    private fun readText(file: Path): String? {
        if (!file.exists()) return null
        return try {
            file.readText()
        } catch (e: Exception) {
            logger.warn("Could not read import migrations from $file", e)
            null
        }
    }

    /**
     * Use [migrations] instead of downloading them, until [parentDisposable] is disposed.
     *
     * This lets tests provide migrations synchronously, without touching the network.
     */
    @TestOnly
    fun useMigrationsForTests(migrations: ImportMigrations, parentDisposable: Disposable) {
        val wasLoadRequested = loadRequested.getAndSet(true)
        val previous = loadedMigrations
        loadedMigrations = migrations
        Disposer.register(parentDisposable) {
            loadedMigrations = previous
            loadRequested.set(wasLoadRequested)
        }
    }
}
