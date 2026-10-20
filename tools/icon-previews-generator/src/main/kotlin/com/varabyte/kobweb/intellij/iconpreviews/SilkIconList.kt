package com.varabyte.kobweb.intellij.iconpreviews

/**
 * Parsers for the icon list files in Kobweb's `silk-icons-*` modules (e.g. `fa-icon-list.txt`), which Kobweb's codegen
 * creates its icon functions from.
 */
object SilkIconList {
    /** Parse an icon list file whose lines look like `fas=align-left,align-right,...` into style ID -> names. */
    fun parseNamesByStyle(text: String, separator: Char): Map<String, List<String>> = contentLines(text)
        .associate { line ->
            val (style, names) = line.split("=", limit = 2)
            style to names.split(",").dropCollisions(separator)
        }

    /**
     * Parse an icon list file of names that apply to every style.
     *
     * Material Symbols' list used to repeat the same names for each style (`mso=...`, `msr=...`, `mss=...`), but now
     * just lists them once, one per line. This accepts either format.
     */
    fun parseNamesForAllStyles(text: String, separator: Char): List<String> = contentLines(text)
        .flatMap { line -> line.substringAfter('=').split(",") }
        .distinct()
        .toList()
        .dropCollisions(separator)

    /** The lines of an icon list file, minus blank lines and `#` comments. */
    private fun contentLines(text: String) = text.lineSequence()
        .map { it.trim() }
        .filter { line -> line.isNotBlank() && !line.startsWith("#") }

    /**
     * Mirror how Kobweb's codegen drops names that would collide once separators are removed (the longest name wins).
     *
     * That guarantees each surviving name is unique ignoring separators and case, which is what lets us map a function
     * like `FaAlignLeft` back to "align-left".
     */
    private fun List<String>.dropCollisions(separator: Char) = this
        .groupBy { it.replace(separator.toString(), "") }
        .map { (_, values) -> values.maxBy { it.length } }
}
