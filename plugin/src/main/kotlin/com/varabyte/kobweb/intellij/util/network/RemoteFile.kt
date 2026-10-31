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
    suspend fun downloadWithRetries(): ByteArray {
        var retryDelay = INITIAL_RETRY_DELAY
        while (true) {
            download()?.let { return it }
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
        }
    }
}
