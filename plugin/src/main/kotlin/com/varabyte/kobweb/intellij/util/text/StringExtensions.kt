package com.varabyte.kobweb.intellij.util.text

/**
 * In the given string, search for all matches in [targets] and replace them all with [replacement].
 *
 * For example, `"Hello world".replaceAll(setOf("e", "o"), "x")` would result in "Hxllx wxrld". Take that, vowels!
 */
fun String.replaceAll(targets: Set<String>, replacement: String): String {
    if (this.isEmpty()) return this

    val targets = targets.minus(replacement) // No need to waste timing replacing a target with itself
    if (targets.isEmpty()) return this

    // Sort descending by length so longer overlapping targets (e.g. "ell" before "el") match first
    val pattern = targets
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }
        .toRegex()

    return this.replace(pattern, replacement)
}
