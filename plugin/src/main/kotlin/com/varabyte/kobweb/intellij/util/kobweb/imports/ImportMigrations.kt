package com.varabyte.kobweb.intellij.util.kobweb.imports

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * A list of declarations that have moved, e.g. from `com.varabyte.kobweb.browser.api` to
 * `com.varabyte.kobweb.api.api`, which lets us offer to update imports that still use the old location.
 *
 * Kotlin's usual tool for this, `@Deprecated` with `ReplaceWith`, doesn't handle declarations that only changed
 * packages: the code using them doesn't change, so all that needs updating is the import, and the old one gets left
 * behind next to the new one, making calls ambiguous. This is especially common with extension functions and
 * properties, which Kobweb uses heavily, so the plugin updates such imports itself.
 *
 * A declaration may move more than once over time, in which case its moves form a chain (e.g. `a.fn` -> `b.fn` ->
 * `c.fn`). See [findMigrationTargets] for how a target is chosen from it.
 */
class ImportMigrations private constructor(private val targets: Map<FqName, FqName>) {
    companion object {
        val Empty = ImportMigrations(emptyMap())

        /**
         * Parse migrations from text where each entry holds the package that declarations moved from, the package they
         * moved to, and then the names of those declarations, all separated by whitespace. Names can continue on the
         * following lines by indenting them, e.g.
         *
         * ```
         * # Comments start with a '#'
         * com.varabyte.kobweb.browser  com.varabyte.kobweb.api  ApiFetcher api
         *   patch post put query
         * ```
         *
         * A blank line ends an entry, while lines holding only a comment are ignored.
         *
         * Only top-level declarations that keep their names are supported (i.e. moves, not renames). The same pair of
         * packages can appear in several entries, but each declaration can only be moved from once.
         *
         * Any line that breaks these rules rejects the whole file, rather than guessing at what was meant. Format
         * changes that older versions of this plugin can't handle are published as a new version of the file instead.
         *
         * @throws IllegalArgumentException if the text isn't in the expected format.
         */
        fun parse(text: String): ImportMigrations {
            class Entry(val lineNumber: Int, val fromPackage: FqName, val toPackage: FqName) {
                val names = mutableSetOf<String>()
            }

            val entries = mutableListOf<Entry>()
            var entry: Entry? = null

            fun endEntry() {
                entry?.let {
                    require(it.names.isNotEmpty()) { "Line ${it.lineNumber}: expected at least one name to move" }
                    entries.add(it)
                }
                entry = null
            }

            text.lineSequence().forEachIndexed { index, rawLine ->
                if (rawLine.isBlank()) {
                    endEntry()
                    return@forEachIndexed
                }
                val line = rawLine.substringBefore('#').trim()
                if (line.isEmpty()) return@forEachIndexed

                fun requireValid(value: Boolean, reason: () -> String) =
                    require(value) { "Line ${index + 1} (\"$line\"): ${reason()}" }

                val parts = line.split(Regex("\\s+"))
                val names = if (rawLine.first().isWhitespace()) {
                    requireValid(entry != null) { "indented lines must continue an entry" }
                    parts
                } else {
                    endEntry()
                    requireValid(parts.size >= 2) { "expected the package moved from, then the package moved to" }
                    val (fromPackage, toPackage) = parts.take(2).map { part ->
                        requireValid(part.split('.').all(StringUtil::isJavaIdentifier)) {
                            "\"$part\" isn't a package name"
                        }
                        FqName(part)
                    }
                    requireValid(fromPackage != toPackage) { "moves declarations to the package they're already in" }
                    entry = Entry(index + 1, fromPackage, toPackage)
                    parts.drop(2)
                }

                val currentEntry = entry!!
                val otherEntriesFromSamePackage = entries.filter { it.fromPackage == currentEntry.fromPackage }
                names.forEach { name ->
                    requireValid(StringUtil.isJavaIdentifier(name)) {
                        "\"$name\" isn't the name of a top-level declaration"
                    }
                    val earlierEntry = otherEntriesFromSamePackage.firstOrNull { name in it.names }
                    requireValid(earlierEntry == null) {
                        "\"${currentEntry.fromPackage}.$name\" was already moved on line ${earlierEntry!!.lineNumber}"
                    }
                    requireValid(currentEntry.names.add(name)) { "\"$name\" was already moved in this entry" }
                }
            }
            endEntry()

            return ImportMigrations(
                entries.flatMap { entry ->
                    entry.names.map { name ->
                        entry.fromPackage.child(Name.identifier(name)) to entry.toPackage.child(Name.identifier(name))
                    }
                }.toMap()
            )
        }
    }

    fun isEmpty() = targets.isEmpty()

    /**
     * Returns every place that [fqName] moved to, newest first, or an empty list if it never moved.
     *
     * Callers should use the first of these that exists in the user's version of the library, falling back to older
     * ones in case it predates the latest move. Any links in between don't need to exist, which matters for
     * declarations that moved several times and whose intermediate locations have since been deleted.
     */
    fun findMigrationTargets(fqName: FqName): List<FqName> {
        val chain = mutableListOf<FqName>()
        // Guard against cycles, e.g. if something was moved back to where it once was
        val visited = mutableSetOf(fqName)
        var current = fqName
        while (true) {
            current = targets[current]?.takeIf { visited.add(it) } ?: break
            chain.add(current)
        }
        return chain.asReversed()
    }
}
