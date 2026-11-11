plugins {
    id("kobweb-compilation-settings")
    alias(libs.plugins.kotlin.jvm)
}

group = "com.varabyte.kobweb.intellij.iconpreviews"
version = libs.versions.kobweb.ide.plugin.get()

dependencies {
    // Provided by the IDE
    compileOnly(kotlin("stdlib"))
    // Facts about each upstream icon set, shared with Kobweb's own icon codegen. The plugin bundles this.
    api(libs.kobweb.icons.model) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
}
