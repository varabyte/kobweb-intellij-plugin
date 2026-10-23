package com.varabyte.kobweb.intellij.util.kobweb

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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

/**
 * The values we use from a [KOBWEB_METADATA_FRONTEND] file (see `FrontendData` in the Kobweb codebase), which Kobweb
 * libraries use to list what they define.
 *
 * @property layouts The library's layouts.
 * @property pages The library's pages.
 */
class KobwebFrontendMetadata(val layouts: List<Layout>, val pages: List<Page>) {
    /**
     * A layout defined by a library (see `LayoutEntry` in the Kobweb codebase).
     *
     * @property fqn The fully qualified name of the layout function, e.g. "com.example.layouts.PageLayout".
     */
    class Layout(val fqn: String)

    /**
     * A page defined by a library (see `PageEntry` in the Kobweb codebase).
     *
     * @property fqn The fully qualified name of the page function, e.g. "com.example.pages.AboutPage".
     * @property route The route the page is served at, e.g. "/about".
     */
    class Page(val fqn: String, val route: String)
}

/**
 * Parses the contents of a [KOBWEB_METADATA_FRONTEND] file, returning null if it's malformed.
 *
 * Individual entries that are malformed (e.g. a page without a route) are skipped.
 */
fun Json.readKobwebFrontendMetadata(text: String): KobwebFrontendMetadata? {
    return try {
        val root = parseToJsonElement(text).jsonObject
        fun entries(listName: String) = root[listName]?.jsonArray.orEmpty().filterIsInstance<JsonObject>()
        fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.contentOrNull

        KobwebFrontendMetadata(
            layouts = entries("layouts").mapNotNull { entry ->
                KobwebFrontendMetadata.Layout(fqn = entry.string("fqn") ?: return@mapNotNull null)
            },
            pages = entries("pages").mapNotNull { entry ->
                KobwebFrontendMetadata.Page(
                    fqn = entry.string("fqn") ?: return@mapNotNull null,
                    route = entry.string("route") ?: return@mapNotNull null,
                )
            },
        )
    } catch (_: Exception) {
        // Malformed metadata. Shouldn't happen, but there's nothing useful we can do about it here.
        null
    }
}

/**
 * Reads this artifact's [KOBWEB_METADATA_FRONTEND] file, where the receiver is the root of the artifact (e.g. a klib).
 *
 * Returns null if the artifact doesn't have the file (e.g. it isn't a Kobweb library), or it's malformed.
 */
fun VirtualFile.readKobwebFrontendMetadata(): KobwebFrontendMetadata? {
    val frontendFile = findFileByRelativePath(KOBWEB_METADATA_FRONTEND) ?: return null
    return Json.readKobwebFrontendMetadata(frontendFile.contentsToByteArray().decodeToString())
}
