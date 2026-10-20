plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.varabyte.kobweb.intellij.iconpreviews"
version = libs.versions.kobweb.ide.plugin.get()

repositories {
    mavenCentral()
}

dependencies {
    // Provided by the IDE
    compileOnly(kotlin("stdlib"))
}
