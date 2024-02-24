package com.varabyte.kobweb.intellij.util.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry

/**
 * A simple heuristic for checking if this project has code in it somewhere that depends on Kobweb.
 *
 * This is useful as users may install the Kobweb plugin for one or two of their projects, but we should stay out of the
 * way in every other kind of project.
 */
fun Project.hasAnyKobwebDependency(): Boolean {
    return this.modules.asSequence()
        .flatMap { module -> module.rootManager.orderEntries.asSequence() }
        .any { orderEntry ->
            when (orderEntry) {
                // Most projects will indicate a dependency on Kobweb via library coordinates, e.g. `com.varabyte.kobweb:core`
                is LibraryOrderEntry -> orderEntry.libraryName.orEmpty().substringBefore(':') == "com.varabyte.kobweb"
                // Very rare, but if a project depends on Kobweb source directly, that counts. This is essentially for
                // the `kobweb/playground` project which devs use to test latest Kobweb on.
                is ModuleOrderEntry -> orderEntry.moduleName.substringBefore('.') == "kobweb"
                else -> false
            }
        }
}
