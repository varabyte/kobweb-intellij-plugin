import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij)
}

group = "com.varabyte.kobweb.intellij"
version = libs.versions.kobweb.ide.plugin.get()

dependencies {
    implementation(project(":kobweb-model"))
    testImplementation(libs.truthish)
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version = "2023.3.2"
    type = "IC" // Target IDE Platform

    plugins = listOf(
        "org.jetbrains.kotlin",
        "org.jetbrains.plugins.gradle",
    )
}

tasks {
    // Set the JVM compatibility versions
    val jvmTarget = JvmTarget.JVM_17
    withType<JavaCompile>().configureEach {
        sourceCompatibility = jvmTarget.target
        targetCompatibility = jvmTarget.target
    }
    kotlin.compilerOptions.jvmTarget = jvmTarget

    // https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-faq.html#how-to-disable-building-searchable-options
    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        sinceBuild = "233"
        untilBuild = "241.*"
    }

    signPlugin {
        certificateChain = System.getenv("CERTIFICATE_CHAIN")
        privateKey = System.getenv("PRIVATE_KEY")
        password = System.getenv("PRIVATE_KEY_PASSWORD")
    }

    publishPlugin {
        token = System.getenv("PUBLISH_TOKEN")
    }
}
