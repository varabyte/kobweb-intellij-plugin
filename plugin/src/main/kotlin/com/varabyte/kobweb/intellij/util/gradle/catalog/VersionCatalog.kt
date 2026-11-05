package com.varabyte.kobweb.intellij.util.gradle.catalog

import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationSpec
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel

/**
 * A view of a Gradle version catalog's versions, libraries, and plugins, keyed by alias.
 *
 * See also: https://docs.gradle.org/current/userguide/version_catalogs.html
 *
 * This wraps the Gradle DSL's [GradleVersionCatalogModel] rather than replacing it, for two reasons:
 * - The DSL describes each entry in the many forms a catalog can be written in, and only tells you indirectly whether a
 *   version refers to a key in the `[versions]` table. Here, every entry is reduced to an [Entry] with a single ID and
 *   a [Version] that says exactly that, which is what comparing two catalogs needs.
 * - Reading a table through the DSL creates it if it's missing. [applyChanges] removes such tables before writing, so
 *   merely looking at a catalog doesn't leave empty tables in it.
 *
 * Changes are made through [model] (e.g. by [copyEntriesIfAbsent]), and the properties here always reflect its current
 * state.
 */
class VersionCatalog(val model: GradleVersionCatalogModel) {
    sealed interface Version {
        /** A reference to an entry in the `[versions]` table, e.g. `version.ref = "kotlin"`. */
        data class Ref(val key: String) : Version

        /** A version written into the entry itself, e.g. `"org.example:lib:1.0"` or `version = "1.0"`. */
        data class Literal(val value: String) : Version
    }

    /**
     * A library or plugin declared in a catalog.
     *
     * @property id A library's module (e.g. `"com.varabyte.kobweb:kobweb-core"`) or a plugin's ID (e.g.
     *   `"com.varabyte.kobweb.application"`).
     * @property version Null if the entry doesn't have one (e.g. it's managed by a BOM) or it's in a form we don't
     *   read (e.g. a rich version like `version = { strictly = "1.0" }`).
     */
    data class Entry(val id: String, val version: Version?)

    /** The `[versions]` table, mapping each key to its version. */
    val versions: Map<String, String>
        get() = model.versionDeclarations().getAll()
            .mapNotNull { (key, version) -> version.compactNotation()?.let { key to it } }
            .toMap()

    val libraries: Map<String, Entry>
        get() = model.libraryDeclarations().getAll().mapValues { (_, library) ->
            val spec = library.getSpec()
            Entry("${spec.getGroup()}:${spec.getName()}", readVersion(library.completeModel(), spec.getVersion()))
        }

    val plugins: Map<String, Entry>
        get() = model.pluginDeclarations().getAll().mapValues { (_, plugin) ->
            val spec = plugin.getSpec()
            Entry(spec.getId(), readVersion(plugin.completeModel(), spec.getVersion()))
        }

    /**
     * Writes the changes made through [model] to the catalog file.
     *
     * Call this instead of the model's own `applyChanges`: reading a table through the model creates it if it's
     * missing, so this first removes new tables that are still empty. (A missing table has no PSI yet, so empty tables
     * that the user wrote are kept.)
     *
     * Like the model's version, this must be called inside a write command.
     */
    fun applyChanges() {
        model.versionDeclarations().takeIf { it.getAll().isEmpty() && it.psiElement == null }?.delete()
        model.libraryDeclarations().takeIf { it.getAll().isEmpty() && it.psiElement == null }?.delete()
        model.pluginDeclarations().takeIf { it.getAll().isEmpty() && it.psiElement == null }?.delete()
        model.applyChanges()
    }
}

/**
 * The alias of the library with [id] (its module, e.g. `"com.varabyte.kobweb:kobweb-core"`), or null if this catalog
 * doesn't declare it. If it's declared under several aliases, the first is returned.
 */
fun VersionCatalog.findLibraryAlias(id: String): String? = libraries.findAlias(id)

/**
 * The alias of the plugin with [id] (e.g. `"com.varabyte.kobweb.library"`), or null if this catalog doesn't declare
 * it. If it's declared under several aliases, the first is returned.
 */
fun VersionCatalog.findPluginAlias(id: String): String? = plugins.findAlias(id)

private fun Map<String, VersionCatalog.Entry>.findAlias(id: String) = entries.firstOrNull { it.value.id == id }?.key

/** The accessor that Kotlin build scripts use for the library with [alias], e.g. `libs.kobweb.core`. */
fun VersionCatalog.libraryAccessor(alias: String) = "${model.catalogName()}.${alias.toAccessorPath()}"

/** The accessor that Kotlin build scripts use for the plugin with [alias], e.g. `libs.plugins.kobweb.library`. */
fun VersionCatalog.pluginAccessor(alias: String) = "${model.catalogName()}.plugins.${alias.toAccessorPath()}"

/**
 * Reads an entry's version, given the model of the whole entry and the version it resolves to.
 *
 * The resolved version alone doesn't say whether it came from a key in the `[versions]` table, so for entries written
 * as a table (e.g. `{ module = "...", version.ref = "kobweb" }`), we check whether their version is a reference.
 */
private fun readVersion(
    entryModel: ResolvedPropertyModel?,
    resolvedVersion: VersionDeclarationSpec?,
): VersionCatalog.Version? {
    val resolved = resolvedVersion?.compactNotation()
    val unresolved = entryModel?.unresolvedModel
    if (unresolved?.valueType == ValueType.MAP) {
        val versionModel = unresolved.getValue(GradlePropertyModel.MAP_TYPE)?.get("version") ?: return null
        return when (versionModel.valueType) {
            ValueType.REFERENCE -> versionModel.dependencies.firstOrNull()?.fullyQualifiedName
                ?.removePrefix("versions.")?.let { VersionCatalog.Version.Ref(it) }
            ValueType.STRING -> resolved?.let { VersionCatalog.Version.Literal(it) }
            else -> null
        }
    }
    // e.g. "com.varabyte.kobweb:kobweb-core:0.25.1"
    return resolved?.let { VersionCatalog.Version.Literal(it) }
}

/**
 * Converts an alias into the path Gradle generates for it in Kotlin build scripts, e.g. `kobweb-core` into
 * `kobweb.core`.
 *
 * Gradle treats `-`, `_`, and `.` as separators. See also:
 * https://docs.gradle.org/current/userguide/version_catalogs.html#sec:mapping-aliases-to-accessors
 */
private fun String.toAccessorPath() = replace('-', '.').replace('_', '.')
