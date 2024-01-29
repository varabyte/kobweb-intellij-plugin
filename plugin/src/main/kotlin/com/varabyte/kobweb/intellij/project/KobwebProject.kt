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
class KobwebProject(
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
        class Local(val module: Module) : Source()

        /**
         * The code for this Kobweb project lives in an external dependency (e.g. a maven artifact).
         *
         * This should be considered read-only code, and refactoring actions should not be available.
         */
        class External(val klib: VirtualFile) : Source()

        override fun equals(other: Any?): Boolean {
            if (other !is Source) return false
            return when {
                this is Local && other is Local -> this.module == other.module
                this is External && other is External -> this.klib == other.klib
                else -> false
            }
        }

        override fun hashCode(): Int {
            return when(this) {
                is Local -> module.hashCode()
                is External -> klib.hashCode()
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is KobwebProject && this.source == other.source
    }

    override fun hashCode(): Int {
        return source.hashCode()
    }

    override fun toString(): String {
        return "KobwebProject(name=${name}, type=${type}, source=${source::class.simpleName})"
    }
}
