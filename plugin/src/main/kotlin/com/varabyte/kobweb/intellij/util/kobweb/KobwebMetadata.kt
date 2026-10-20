package com.varabyte.kobweb.intellij.util.kobweb

// Paths to metadata files that Kobweb bundles into the artifacts it builds (relative to the artifact's root).
//
// These mirror the `KOBWEB_METADATA_*` constants in Kobweb's `processor-common` module.

const val KOBWEB_METADATA_SUBFOLDER = "META-INF/kobweb"

const val KOBWEB_METADATA_FRONTEND = "$KOBWEB_METADATA_SUBFOLDER/frontend.json"
const val KOBWEB_METADATA_BACKEND = "$KOBWEB_METADATA_SUBFOLDER/backend.json"

// Top-level metadata files that identify what sort of Kobweb artifact this is
const val KOBWEB_METADATA_LIBRARY = "$KOBWEB_METADATA_SUBFOLDER/library.json"
const val KOBWEB_METADATA_WORKER = "$KOBWEB_METADATA_SUBFOLDER/worker.json"
