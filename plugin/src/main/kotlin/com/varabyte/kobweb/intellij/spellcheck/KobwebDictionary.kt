package com.varabyte.kobweb.intellij.spellcheck

import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.RuntimeDictionaryProvider

class KobwebDictionaryProvider : RuntimeDictionaryProvider {
    override fun getDictionaries(): Array<Dictionary> = arrayOf(KobwebDictionary())
}

/**
 * A collection of nonstandard words that are commonly encountered in Kobweb projects
 */
class KobwebDictionary : Dictionary {
    private val words = setOf(
        "frontmatter",
        "kobweb",
        "transferables",
        "varabyte",
    )

    override fun getName(): String {
        return "Kobweb Dictionary"
    }

    override fun contains(word: String): Boolean {
        return words.contains(word.lowercase())
    }

    override fun getWords() = emptySet<String>()
}
