package com.varabyte.kobweb.intellij.model

/**
 * A Kobweb project is one that applies one of the Kobweb gradle plugins.
 */
enum class KobwebProjectType {
    Application,
    Library,
    Worker,
}

/**
 * A small IDEA-side model for surfacing the Kobweb plugin type associated with a Gradle module.
 */
interface KobwebModel {
    val projectType: KobwebProjectType
}

data class DefaultKobwebModel(
    override val projectType: KobwebProjectType,
) : KobwebModel
