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
 *
 * Note that IntelliJ saves this model between sessions and restores it on startup, until the next Gradle sync replaces
 * it. As a result, any value added after the plugin's first release must be nullable, since it will be missing from
 * models saved by earlier versions of the plugin.
 */
interface KobwebModel : Serializable {
    val projectType: KobwebProjectType

    /**
     * The Gradle project's group (e.g. "com.example").
     *
     * In various contexts, Kobweb treats paths that start with a leading period as relative to this value,
     * e.g. ".components.layouts" expands to "com.example.components.layouts".
     *
     * This is null if this model was saved by an older version of the plugin, until the next Gradle sync.
     */
    val group: String?

    /**
     * The root package of all pages, from the `kobweb { pagesPackage.set(...) }` value in the build script (e.g.
     * ".pages").
     *
     * Like other Kobweb packages, this may start with a leading period, in which case it is relative to [group].
     *
     * This is null if this model was saved by an older version of the plugin, until the next Gradle sync.
     */
    val pagesPackage: String?

    /**
     * The folder inside the project's resources that Kobweb serves files from as is, from the
     * `kobweb { publicPath.set(...) }` value in the build script (e.g. "public", for "src/jsMain/resources/public").
     *
     * This is null if this model was saved by an older version of the plugin, until the next Gradle sync.
     */
    val publicPath: String?

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
