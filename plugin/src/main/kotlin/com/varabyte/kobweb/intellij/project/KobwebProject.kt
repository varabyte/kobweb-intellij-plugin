package com.varabyte.kobweb.intellij.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.model.WorkerBlock

/**
 * A Kobweb Project is a module that has applied one of the Kobweb Gradle Plugins.
 *
 * It can either be a local project inside the user's workspace or an external dependency (e.g. a maven artifact).
 *
 * @property name A human-readable name for this project.
 * @property group The Gradle group of this project (e.g. "com.example"), which Kobweb uses to expand paths that begin
 *   with a leading period. This may not set for external projects, as older versions of Kobweb did not report it.
 *   However, given that a group is generally only used internally within a Kobweb module, this isn't really an issue.
 *   At worst, it means we will not be able to navigate Kobweb shortcuts while browsing such a project.
 * @property worker Values from the `kobweb { worker { ... } }` block. Only ever set for local worker projects.
 */
data class KobwebProject(
    val name: String,
    val type: KobwebProjectType,
    val source: Source,
    val group: String? = null,
    val worker: WorkerBlock? = null,
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
