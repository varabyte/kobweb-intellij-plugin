package com.varabyte.kobweb.intellij.spellcheck

import com.intellij.spellchecker.BundledDictionaryProvider

/**
 * Provides a bundled dictionary of common Kobweb-specific terms.
 */
class KobwebBundledDictionaryProvider : BundledDictionaryProvider {
    override fun getBundledDictionaries(): Array<String> = arrayOf("/dictionaries/kobweb.dic")
}
