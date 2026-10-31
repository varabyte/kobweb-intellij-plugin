package com.varabyte.kobweb.intellij.util.network

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private const val NETWORK_TIMEOUT_MS = 5000
private val INITIAL_RETRY_DELAY = 30.seconds
private val MAX_RETRY_DELAY = 1.hours
// With the delays above, this keeps retrying for at least 7.5 minutes, which outlasts GitHub's raw file cache (5
// minutes)
private const val MAX_INVALID_DOWNLOADS = 5

/**
 * A file hosted online, e.g. data the plugin downloads from the varabyte/data repo.
 */
class RemoteFile(val url: String) {
    private val logger = logger<RemoteFile>()

    /**
     * Downloads the file, returning null if that fails (e.g. when offline).
     *
     * This blocks on network I/O, so it should only be called off the EDT (e.g. on `Dispatchers.IO`).
     */
    fun download(): ByteArray? {
        return try {
            HttpRequests.request(url)
                .connectTimeout(NETWORK_TIMEOUT_MS)
                .readTimeout(NETWORK_TIMEOUT_MS)
                .readBytes(null)
        } catch (e: IOException) {
            logger.info("Could not download $url: ${e.message}")
            null
        }
    }

    /**
     * Keeps trying to download the file until it succeeds, waiting longer after each failure.
     *
     * This covers the IDE starting up while offline (e.g. before reconnecting to Wi-Fi), so users don't have to restart
     * it to get the latest data. Retries stop when the calling coroutine is cancelled, e.g. when the IDE shuts down.
     */
    // Never null, as every download passes this check
    suspend fun downloadWithRetries(): ByteArray = downloadWithRetries(isValid = { true })!!

    /**
     * Like the other [downloadWithRetries], but also checks each download with [isValid] (e.g. against an expected
     * checksum), retrying a few times before giving up.
     *
     * A failed check isn't always a mistake. Data that's published as several files (e.g. a manifest listing the
     * checksums of the other files) can briefly be out of sync, as a CDN caches each file separately, so right after
     * publishing, it may serve the new manifest along with an older copy of a file it lists. That sorts itself out
     * within a few minutes, so we retry for a bit longer than that. If the check still fails, the data was most likely
     * published incorrectly, and downloading it again would never help.
     *
     * @return the downloaded file, or null if it kept failing [isValid].
     */
    suspend fun downloadWithRetries(isValid: (ByteArray) -> Boolean): ByteArray? {
        var retryDelay = INITIAL_RETRY_DELAY
        var invalidDownloads = 0
        while (true) {
            val bytes = download()
            if (bytes != null) {
                if (isValid(bytes)) return bytes
                if (++invalidDownloads >= MAX_INVALID_DOWNLOADS) {
                    logger.warn("Gave up on $url after downloading an unexpected version of it $invalidDownloads times")
                    return null
                }
                logger.info("Downloaded an unexpected version of $url")
            }
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
        }
    }
}
