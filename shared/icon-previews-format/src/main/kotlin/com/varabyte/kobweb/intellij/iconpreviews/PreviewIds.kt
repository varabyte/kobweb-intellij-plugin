package com.varabyte.kobweb.intellij.iconpreviews

import com.varabyte.kobweb.icons.model.fa.FaIconSet
import com.varabyte.kobweb.icons.model.material.MdiIconSet
import com.varabyte.kobweb.icons.model.material.MsIconSet

// The set and style IDs in icon preview data. These are short names that only the plugin made up and cares about. If
// they happen to match codes used by upstream icon sets, that's fine, but they don't need to stay in sync. They can't
// change, though, since older plugin versions read the same data.

/** The ID of Font Awesome's [major] version, e.g. "fa7". Each major version has its own data, as their icons differ. */
@Suppress("UnusedReceiverParameter") // Receiver used for scoping
fun FaIconSet.previewId(major: Int): String = "fa$major"

val FaIconSet.Style.previewId: String
    get() = when (this) {
        FaIconSet.Style.SOLID -> "fas"
        FaIconSet.Style.REGULAR -> "far"
        FaIconSet.Style.BRANDS -> "fab"
    }

val MdiIconSet.Style.previewId: String
    get() = when (this) {
        MdiIconSet.Style.FILLED -> "mdi"
        MdiIconSet.Style.OUTLINED -> "mdio"
        MdiIconSet.Style.ROUNDED -> "mdir"
        MdiIconSet.Style.SHARP -> "mdis"
        MdiIconSet.Style.TWO_TONED -> "mdit"
    }

val MsIconSet.Style.previewId: String
    get() = when (this) {
        MsIconSet.Style.OUTLINED -> "mso"
        MsIconSet.Style.ROUNDED -> "msr"
        MsIconSet.Style.SHARP -> "mss"
    }
