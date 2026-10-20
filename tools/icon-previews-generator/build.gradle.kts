plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.varabyte.kobweb.intellij.iconpreviews"
version = libs.versions.kobweb.ide.plugin.get()

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.kotlinx.serialization.json)
    // For converting SVG path data into Java2D shapes
    implementation(libs.batik.parser)
    implementation(project(":shared:icon-previews-format"))
}

// Usage: ./gradlew :tools:icon-previews-generator:generate [-PkobwebRef=<branch, tag, or commit>]
//
// Downloads Kobweb's icon lists plus the upstream icon fonts, then writes compact outline data for every icon into
// build/icon-previews/data (along with contact sheets in build/icon-previews/sheets, for eyeballing the results).
//
// To publish, copy the files in build/icon-previews/data into `kobweb-intellij-plugin/icon-previews/` in the
// https://github.com/varabyte/data repo, which is where the plugin downloads them from.
tasks.register<JavaExec>("generate") {
    group = "icon previews"
    description = "Generate gutter icon preview data for Kobweb's silk-icons-* modules."

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.varabyte.kobweb.intellij.iconpreviews.MainKt"
    jvmArgs("-Djava.awt.headless=true")

    val kobwebRef = providers.gradleProperty("kobwebRef").orElse("main")
    val rootDir = layout.buildDirectory.dir("icon-previews")
    argumentProviders.add(CommandLineArgumentProvider {
        val root = rootDir.get().asFile
        listOf(
            "--kobweb-ref", kobwebRef.get(),
            "--out", root.resolve("data").path,
            "--sheets", root.resolve("sheets").path,
            "--cache", root.resolve("cache").path,
        )
    })

    // Upstream sources are fetched at runtime (and may change under us), so never consider this task up to date.
    outputs.upToDateWhen { false }
}
