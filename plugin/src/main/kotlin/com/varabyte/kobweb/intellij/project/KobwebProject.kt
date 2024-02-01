package com.varabyte.kobweb.intellij.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.varabyte.kobweb.intellij.model.KobwebProjectType

/**
 * A Kobweb Project is a module that has applied one of the Kobweb Gradle Plugins.
 *
 * It can either be a local project inside the user's workspace or an external dependency (e.g. a maven artifact).
 *
 * @property name A human-readable name for this project.
 */
data class KobwebProject(
    val name: String,
    val type: KobwebProjectType,
    val source: Source,
) {
    /**
     * Where the code for this Kobweb project lives, i.e. in the user's project or as an external dependency.
     */
    sealed class Source {
        /**
         * The code for this Kobweb project lives in the user's project somewhere.
         *
         * This should be considered editable code, and refactoring actions should be available.
         */
        data class Local(val module: Module) : Source()

        /**
         * The code for this Kobweb project lives in an external dependency (e.g. a maven artifact).
         *
         * This should be considered read-only code, and refactoring actions should not be available.
         */
        data class External(val klib: VirtualFile) : Source()
    }
}
