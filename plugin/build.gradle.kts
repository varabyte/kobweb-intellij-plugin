import org.jetbrains.changelog.Changelog
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij)
    alias(libs.plugins.jetbrains.changelog)
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

changelog {
    path.set(file("../CHANGELOG.md").canonicalPath)
    repositoryUrl.set("https://github.com/varabyte/kobweb-intellij-plugin")
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
        // Useful statistics: https://plugins.jetbrains.com/docs/marketplace/product-versions-in-use-statistics.html
        sinceBuild = "233"
        untilBuild = "241.*"

        changeNotes = provider {
            changelog.renderItem(
                changelog
                    .getLatest()
                    .withHeader(false)
                    .withEmptySections(false),
                Changelog.OutputType.HTML
            )
        }
    }

    run {
        var credentialsFound = false

        signPlugin {
            val password = (findProperty("kobweb.intellij.plugin.password") as? String) ?: return@signPlugin
            val key = (findProperty("kobweb.intellij.plugin.key") as? String) ?: return@signPlugin
            val cert = (findProperty("kobweb.intellij.plugin.cert") as? String) ?: return@signPlugin
            credentialsFound = true

            this.password = password
            this.privateKey = key
            this.certificateChain = cert
        }

        if (!credentialsFound) {
            logger.info("Credentials not found. The plugin cannot be signed on this machine.")
        } else {
            logger.info("Credentials found. The plugin can be signed on this machine.")
        }
    }

    run {
        var tokenFound = false
        publishPlugin {
            val token = (findProperty("kobweb.intellij.plugin.token") as? String) ?: return@publishPlugin
            tokenFound = true
            this.token = token
        }

        if (!tokenFound) {
            logger.info("Credentials not found. The plugin cannot be published from this machine.")
        } else {
            logger.info("Credentials found. The plugin can be published from this machine.")
        }
    }
}
