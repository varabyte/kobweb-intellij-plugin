pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    includeBuild("gradle/build-logic")
}

rootProject.name = "kobweb-intellij-plugin"

include(":kobweb-model")
include(":plugin")
include(":shared:icon-previews-format")
include(":tools:icon-previews-generator")
