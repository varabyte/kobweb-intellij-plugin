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

interface KobwebModel : Serializable {
    val projectType: KobwebProjectType
}
