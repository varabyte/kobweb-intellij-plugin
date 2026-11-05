package com.varabyte.kobweb.intellij.util.gradle.catalog

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.varabyte.kobweb.intellij.util.gradle.catalog.VersionCatalog.Entry
import com.varabyte.kobweb.intellij.util.gradle.catalog.VersionCatalog.Version

/**
 * Copies the entries of the [from] catalog with the given aliases that this catalog doesn't declare yet.
 *
 * An entry counts as already declared if this catalog has an entry with the same library module or plugin ID,
 * whatever its alias. For example, if [from] declares
 * `kobweb-core = { module = "com.varabyte.kobweb:kobweb-core", ... }` and this catalog declares
 * `core = "com.varabyte.kobweb:kobweb-core:0.25.1"`, then `kobweb-core` is already declared here (as `core`) and isn't
 * copied.
 *
 * Copied entries keep their alias, unless this catalog already uses it for something else, in which case it gets a
 * number (e.g. `kobweb-core2`). Use [findLibraryAlias] and [findPluginAlias] afterwards to find the aliases this
 * catalog ended up with.
 *
 * Versions are added only when needed. If this catalog lacks a version that [from] refers to (e.g.
 * `version.ref = "kobweb"`), but already declares another [from] entry that shares that version (e.g. a Kobweb
 * library declared with `version = "0.25.1"`), its version is reused, so the project stays on a single Kobweb version.
 * Otherwise, the version is copied over too.
 *
 * This only changes the model. Callers write the changes with [VersionCatalog.applyChanges].
 */
fun VersionCatalog.copyEntriesIfAbsent(
    from: VersionCatalog,
    libraryAliases: List<String>,
    pluginAliases: List<String>,
) {
    // The version of a [from] version key, as this catalog spells it. Filled in as versions get resolved, so that a
    // key is only added to this catalog once.
    val resolvedVersions = mutableMapOf<String, Version>()

    fun resolveVersion(fromVersion: Version?): Version? {
        if (fromVersion !is Version.Ref) return fromVersion
        val key = fromVersion.key
        return resolvedVersions.getOrPut(key) {
            val versions = versions
            fun addKeyIfMissing(value: String): Version {
                if (key !in versions) model.versionDeclarations().addDeclaration(key, value)
                return fromVersion
            }

            // The version of an entry in this catalog that [from] says shares this version
            val siblingVersion = Section.entries.firstNotNullOfOrNull { section ->
                from.entries(section).values
                    .filter { it.version == fromVersion }
                    .firstNotNullOfOrNull { sibling -> findAlias(section, sibling.id) }
                    ?.let { alias -> entries(section).getValue(alias).version }
            }
            when (siblingVersion) {
                is Version.Ref if siblingVersion.key in versions -> siblingVersion
                // Prefer a key over repeating the literal in each added entry, unless the key means something else
                is Version.Literal -> if (key in versions) siblingVersion else addKeyIfMissing(siblingVersion.value)
                else -> addKeyIfMissing(
                    from.versions[key] ?: error("Catalog to copy from is missing version \"$key\"")
                )
            }
        }
    }

    fun copyIfAbsent(section: Section, aliases: List<String>) {
        for (fromAlias in aliases) {
            val fromEntry = from.entries(section)[fromAlias]
                ?: error("Catalog to copy from is missing ${section.name.lowercase()} entry \"$fromAlias\"")
            if (findAlias(section, fromEntry.id) != null) continue

            val takenAccessors = entries(section).keys.map { accessor(section, it) }.toSet()
            val alias = generateSequence(1) { it + 1 }
                .map { n -> if (n == 1) fromAlias else "$fromAlias$n" }
                .first { accessor(section, it) !in takenAccessors }
            addEntry(section, alias, Entry(fromEntry.id, resolveVersion(fromEntry.version)))
        }
    }

    copyIfAbsent(Section.LIBRARIES, libraryAliases)
    copyIfAbsent(Section.PLUGINS, pluginAliases)
}

private enum class Section {
    LIBRARIES,
    PLUGINS,
}

private fun VersionCatalog.entries(section: Section) = when (section) {
    Section.LIBRARIES -> libraries
    Section.PLUGINS -> plugins
}

private fun VersionCatalog.accessor(section: Section, alias: String) = when (section) {
    Section.LIBRARIES -> libraryAccessor(alias)
    Section.PLUGINS -> pluginAccessor(alias)
}

private fun VersionCatalog.findAlias(section: Section, id: String) = when (section) {
    Section.LIBRARIES -> findLibraryAlias(id)
    Section.PLUGINS -> findPluginAlias(id)
}

private fun VersionCatalog.addEntry(section: Section, alias: String, entry: Entry) {
    val version = entry.version
    when (section) {
        Section.LIBRARIES -> {
            val libraries = model.libraryDeclarations()
            val (group, name) = entry.id.split(':', limit = 2)
            when (version) {
                is Version.Ref -> libraries.addDeclaration(alias, name, group, toReference(version))
                is Version.Literal -> libraries.addDeclaration(alias, "${entry.id}:${version.value}")
                null -> libraries.addDeclaration(alias, name, group)
            }
        }

        Section.PLUGINS -> {
            val plugins = model.pluginDeclarations()
            when (version) {
                is Version.Ref -> plugins.addDeclaration(alias, entry.id, toReference(version))
                is Version.Literal -> plugins.addDeclaration(alias, "${entry.id}:${version.value}")
                null -> plugins.addDeclaration(alias, entry.id)
            }
        }
    }
}

private fun VersionCatalog.toReference(version: Version.Ref) =
    ReferenceTo(model.versionDeclarations().getAll().getValue(version.key))
