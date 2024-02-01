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
}
