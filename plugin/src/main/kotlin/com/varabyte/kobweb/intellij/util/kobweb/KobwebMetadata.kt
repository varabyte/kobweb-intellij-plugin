package com.varabyte.kobweb.intellij.util.kobweb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Paths to metadata files that Kobweb bundles into the artifacts it builds (relative to the artifact's root).
//
// These mirror the `KOBWEB_METADATA_*` constants in Kobweb's `processor-common` module.

const val KOBWEB_METADATA_SUBFOLDER = "META-INF/kobweb"

// General information about the module that produced this artifact
const val KOBWEB_METADATA_MODULE = "$KOBWEB_METADATA_SUBFOLDER/module.json"

const val KOBWEB_METADATA_FRONTEND = "$KOBWEB_METADATA_SUBFOLDER/frontend.json"
const val KOBWEB_METADATA_BACKEND = "$KOBWEB_METADATA_SUBFOLDER/backend.json"

// Top-level metadata files that identify what sort of Kobweb artifact this is
const val KOBWEB_METADATA_LIBRARY = "$KOBWEB_METADATA_SUBFOLDER/library.json"
const val KOBWEB_METADATA_WORKER = "$KOBWEB_METADATA_SUBFOLDER/worker.json"

/**
 * The values in a [KOBWEB_METADATA_MODULE] file (see `ModuleMetadata` in the Kobweb codebase).
 *
 * @property kobwebVersion The version of Kobweb this module was built with, e.g. "0.25.2".
 * @property group The Gradle group of the project that produced this module. Kobweb only started recording this in
 *   0.25.2, so it will be null for artifacts built with older versions.
 */
class KobwebModuleMetadata(val kobwebVersion: String, val group: String?)

/**
 * Parses the contents of a [KOBWEB_METADATA_MODULE] file, returning null if it's malformed.
 */
fun Json.readKobwebModuleMetadata(text: String): KobwebModuleMetadata? {
    return try {
        val root = parseToJsonElement(text).jsonObject
        KobwebModuleMetadata(
            // Always recorded, so if it's missing, the file is malformed
            kobwebVersion = root["kobwebVersion"]?.jsonPrimitive?.contentOrNull ?: return null,
            group = root["group"]?.jsonPrimitive?.contentOrNull,
        )
    } catch (_: Exception) {
        // Malformed metadata. Shouldn't happen, but there's nothing useful we can do about it here.
        null
    }
}
