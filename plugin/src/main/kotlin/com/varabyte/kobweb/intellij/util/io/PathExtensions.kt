package com.varabyte.kobweb.intellij.util.io

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes

/**
 * Writes [bytes] to this file (creating its parent directories if needed) by way of a temporary file next to it, so
 * that readers never see a partially written file.
 */
fun Path.writeBytesAtomically(bytes: ByteArray) {
    parent.createDirectories()
    val tempFile = Files.createTempFile(parent, fileName.toString(), ".tmp")
    try {
        tempFile.writeBytes(bytes)
        Files.move(tempFile, this, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        // Don't leave partial files behind if writing or moving failed (a no-op if the move succeeded)
        tempFile.deleteIfExists()
    }
}
