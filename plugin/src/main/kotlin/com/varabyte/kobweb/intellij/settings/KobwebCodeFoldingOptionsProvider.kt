package com.varabyte.kobweb.intellij.settings

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.options.BeanConfigurable
import com.varabyte.kobweb.intellij.util.ux.UxGlobals

/**
 * Adds Kobweb's options to the "Fold by default" section of Settings > Editor > General > Code Folding.
 */
class KobwebCodeFoldingOptionsProvider : BeanConfigurable<KobwebAppSettingsService.State.Folding>(
    KobwebAppSettingsService.getInstance().state.folding,
    UxGlobals.textBundle.message("kobweb.name"),
), CodeFoldingOptionsProvider {
    init {
        val folding = KobwebAppSettingsService.getInstance().state.folding
        checkBox(
            UxGlobals.textBundle.message("folding.percent.encoded.strings.name"),
            { folding.collapsePercentEncodedStrings },
            { value -> folding.collapsePercentEncodedStrings = value },
        )
    }
}
