pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "kobweb-intellij-plugin"

include(":kobweb-model")
include(":plugin")
include(":shared:icon-previews-format")
include(":tools:icon-previews-generator")
