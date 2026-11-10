plugins {
    id("kobweb-compilation-settings")
    alias(libs.plugins.kotlin.jvm)
}

group = "com.varabyte.kobweb.intellij.iconpreviews"
version = libs.versions.kobweb.ide.plugin.get()

dependencies {
    implementation(kotlin("stdlib"))
    // Facts about each upstream icon set, and parsers for its files, shared with Kobweb's own icon codegen
    implementation(libs.kobweb.icons.parsers)
    implementation(libs.kotlinx.serialization.json)
    // For converting SVG path data into Java2D shapes
    implementation(libs.batik.parser)
    implementation(project(":shared:icon-previews-format"))
}

// Usage: ./gradlew :tools:icon-previews-generator:generate
//
// Downloads the newest upstream release of each icon set (and of each major version, for sets that offer more than
// one), then writes compact outline data for every icon into build/icon-previews/data (along with contact sheets in
// build/icon-previews/sheets, for eyeballing the results).
//
// NOTE: Currently, only Font Awesome offers more than one major version (fa6.bin, fa7.bin, plus a copy of FA 6 as
// fa.bin for legacy plugin versions).
//
// To publish, copy the files in build/icon-previews/data into `kobweb-intellij-plugin/icon-previews/` in the
// https://github.com/varabyte/data repo, which is where the plugin downloads them from.
tasks.register<JavaExec>("generate") {
    group = "icon previews"
    description = "Generate gutter icon preview data for Kobweb's silk-icons-* modules."

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.varabyte.kobweb.intellij.iconpreviews.MainKt"
    jvmArgs("-Djava.awt.headless=true")

    val rootDir = layout.buildDirectory.dir("icon-previews")
    argumentProviders.add(CommandLineArgumentProvider {
        val root = rootDir.get().asFile
        listOf(
            "--out", root.resolve("data").path,
            "--sheets", root.resolve("sheets").path,
            "--cache", root.resolve("cache").path,
        )
    })

    // Upstream sources are fetched at runtime (and may change under us), so never consider this task up to date.
    outputs.upToDateWhen { false }
}
