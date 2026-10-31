package com.varabyte.kobweb.intellij.services.imports

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.HttpRequests
import com.varabyte.kobweb.intellij.util.kobweb.imports.ImportMigrations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds

// Bump this (and publish the file under a new version folder) if the format ever changes in a way that older versions
// of this plugin can't handle, so that they can keep using the old file
private const val FORMAT_VERSION = 1
private const val DATA_URL =
    "https://raw.githubusercontent.com/varabyte/data/main/kobweb-intellij-plugin/import-migrations/v$FORMAT_VERSION/migrations.txt"
private const val NETWORK_TIMEOUT_MS = 5000
private const val INITIAL_RETRY_DELAY_MS = 30_000L
private const val MAX_RETRY_DELAY_MS = 60 * 60 * 1000L

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

            val downloadedText = downloadWithRetries()
            if (downloadedText == cachedText) return@launch
            // If the published file is broken, keep using the cached copy (if any) rather than caching the broken one.
            // Retrying won't help here, but a fixed file will be picked up after a restart.
            val downloaded = parse(downloadedText, source = DATA_URL) ?: return@launch
            publish(downloaded)
            writeCache(downloadedText)
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
     * Keep trying to download the file until it succeeds, waiting longer after each failure.
     *
     * This covers the IDE starting up while offline (e.g. before reconnecting to Wi-Fi), so users don't have to restart
     * it to get the latest list. Retries stop when the IDE shuts down, as that cancels the service's coroutine scope.
     */
    private suspend fun downloadWithRetries(): String {
        var retryDelayMs = INITIAL_RETRY_DELAY_MS
        while (true) {
            download()?.let { return it }
            delay(retryDelayMs.milliseconds)
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
        }
    }

    private fun download(): String? {
        return try {
            HttpRequests.request(DATA_URL)
                .connectTimeout(NETWORK_TIMEOUT_MS)
                .readTimeout(NETWORK_TIMEOUT_MS)
                .readString()
        } catch (e: IOException) {
            logger.info("Could not download import migrations: ${e.message}")
            null
        }
    }

    private fun writeCache(text: String) {
        try {
            cachedFile.parent.createDirectories()
            val tempFile = Files.createTempFile(cachedFile.parent, "migrations", ".tmp")
            try {
                tempFile.writeText(text)
                Files.move(tempFile, cachedFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                // Don't leave partial files behind if writing or moving failed (a no-op if the move succeeded)
                tempFile.deleteIfExists()
            }
        } catch (e: Exception) {
            logger.info("Could not cache import migrations", e)
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
