package com.varabyte.kobweb.intellij.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

/**
 * The colors that Kobweb highlights code with, which users can change in the Kobweb section of the color scheme
 * settings (see [KobwebColorSettingsPage]).
 */
object KobwebHighlightingColors {
    /**
     * How the values that fill a route's dynamic segments are highlighted, e.g. "bob" in `"/users/bob/posts"` (for a
     * page served at "/users/{user}/posts").
     */
    val DYNAMIC_ROUTE_SEGMENT_VALUE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "KOBWEB_DYNAMIC_ROUTE_SEGMENT_VALUE",
        // Like escape sequences, these values stand out from the rest of the string while still being part of it
        DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE,
    )
}
