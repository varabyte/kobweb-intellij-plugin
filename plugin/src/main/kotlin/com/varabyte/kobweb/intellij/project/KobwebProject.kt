package com.varabyte.kobweb.intellij.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.varabyte.kobweb.intellij.util.kobweb.isInKobwebReadContext
import com.varabyte.kobweb.intellij.util.kobweb.isInKobwebWriteContext

/**
 * A Kobweb Project is a module that has applied one of the Kobweb Gradle Plugins.
 *
 * It can either be a local project inside the user's workspace or an external dependency (e.g. a maven artifact).
 */
class KobwebProject(
    val name: String,
    val type: Type,
    val source: Source,
) {
    enum class Type {
        Application,
        Library,
        Worker,
    }

    /**
     * A collection of useful sets of [Type]s.
     *
     * These can be useful to pass into the [isInKobwebReadContext] and [isInKobwebWriteContext] extension methods.
     */
    object Types {
        /**
         * The set of all Kobweb project types.
         */
        val All = Type.entries.toSet()

        /**
         * The set of core Kobweb project types that affect the frontend DOM / backend API routes.
         */
        val Framework = setOf(Type.Application, Type.Library)

        val WorkerOnly = setOf(Type.Worker)
    }

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
        class External(val jar: VirtualFile) : Source()

        override fun equals(other: Any?): Boolean {
            if (other !is Source) return false
            return when {
                this is Local && other is Local -> this.module == other.module
                this is External && other is External -> this.jar == other.jar
                else -> false
            }
        }

        override fun hashCode(): Int {
            return when(this) {
                is Local -> module.hashCode()
                is External -> jar.hashCode()
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
        return "KobwebProject(name='$name', type=$type, source=${source::class.simpleName})"
    }
}
