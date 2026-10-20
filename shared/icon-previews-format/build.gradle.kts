plugins {
    id("kobweb-compilation-settings")
    alias(libs.plugins.kotlin.jvm)
}

group = "com.varabyte.kobweb.intellij.iconpreviews"
version = libs.versions.kobweb.ide.plugin.get()

dependencies {
    // Provided by the IDE
    compileOnly(kotlin("stdlib"))
}
