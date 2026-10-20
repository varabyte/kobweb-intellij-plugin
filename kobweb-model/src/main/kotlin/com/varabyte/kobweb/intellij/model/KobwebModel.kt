package com.varabyte.kobweb.intellij.model

import java.io.Serializable

/**
 * A Kobweb project is one that applies one of the Kobweb gradle plugins.
 */
enum class KobwebProjectType {
    Application,
    Library,
    Worker,
}

/**
 * A collection of data surfaced about a Kobweb project.
 *
 * This model is used as a way to communicate information between a Gradle project and the Kobweb IntelliJ plugin (which
 * is why it is serializable).
 */
interface KobwebModel : Serializable {
    val projectType: KobwebProjectType

    /**
     * The Gradle project's group (e.g. "com.example").
     *
     * In various contexts, Kobweb treats paths that start with a leading period as relative to this value,
     * e.g. ".components.layouts" expands to "com.example.components.layouts".
     */
    val group: String

    /**
     * Values from the `kobweb { worker { ... } }` block in a worker project's build script.
     *
     * This is always null for non-worker projects.
     */
    val worker: WorkerBlock?
}

/**
 * A subset of the `kobweb.worker` extension from the upstream Kobweb Gradle plugin.
 */
data class WorkerBlock(
    val fqcn: String? = null,
) : Serializable
