package com.varabyte.kobweb.intellij.util.kobweb

import com.varabyte.kobweb.intellij.util.kotlin.child
import org.jetbrains.kotlin.name.FqName

/**
 * Utilities for Kobweb's package shortcuts, where a leading period means a name is relative to some base package.
 *
 * For example, with a base of "com.example", ".components.layouts.PageLayout" is shorthand for
 * "com.example.components.layouts.PageLayout".
 *
 * What the base is depends on the feature, e.g. `@Layout` paths are relative to the project's group, while a worker's
 * `fqcn` override is relative to its factory's package.
 */
object PackageShortcuts {
    /**
     * Expand a (possibly) relative name into a fully qualified one.
     *
     * A name without a leading period is assumed to already be fully qualified, and "." on its own refers to the base
     * itself.
     *
     * @param base The package that relative names are relative to, or null if unknown.
     * @return null if [name] is relative but [base] is unknown.
     */
    fun resolve(base: FqName?, name: String): FqName? {
        if (!name.startsWith('.')) return FqName(name)
        if (base == null) return null

        val relativeName = name.removePrefix(".")
        return if (relativeName.isEmpty()) base else base.child(FqName(relativeName))
    }

    /**
     * The inverse of [resolve], converting a fully qualified name into a relative one when it lives under [base].
     *
     * Unlike `java.nio.file.Path.relativize`, which this is named after, names that don't live under [base] are
     * returned unchanged (as fully qualified names), since package shortcuts can't express a parent package.
     */
    fun relativize(base: FqName?, fqn: FqName): String {
        val fqnStr = fqn.asString()
        if (base == null || base.isRoot) return fqnStr

        val baseStr = base.asString()
        return if (fqnStr.startsWith("$baseStr.")) fqnStr.removePrefix(baseStr) else fqnStr
    }
}
