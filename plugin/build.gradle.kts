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
    // Interesting statistics: https://plugins.jetbrains.com/docs/marketplace/product-versions-in-use-statistics.html
    // We target 2023.3 for:
    // - ProjectActivity (available since 2023.1)
    // - Kotlin 1.9 support
    version = "2023.3"
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

fun Project.isSnapshot() = version.toString().endsWith("-SNAPSHOT")

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
        //sinceBuild derived from intellij.version
        untilBuild = "241.*" // Keep up to date with EAP

        changeNotes = provider {
            val projectVersion = project.version.toString()
            val changelogVersion = projectVersion.removeSuffix("-SNAPSHOT")

            val changelogItem = changelog.getOrNull(changelogVersion) ?: if (project.isSnapshot()) {
                Changelog.Item(
                    version = changelogVersion,
                    header = "Changelog $changelogVersion not found",
                    summary = "**Note to developer:** This snapshot build does not have any changelog entries yet.\n\nConsider adding a `[$changelogVersion]` section to CHANGELOG.md.\n\n**This will become an error if not done before building the non-snapshot release.**"
                )
            } else {
                throw GradleException("Section `[$changelogVersion]` must be added to CHANGELOG.md before building a release build.")
            }

            changelog.renderItem(
                changelogItem.withEmptySections(false),
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

            if (project.isSnapshot()) {
                channels.set(listOf("eap"))
            }
        }

        if (!tokenFound) {
            logger.info("Credentials not found. The plugin cannot be published from this machine.")
        } else {
            logger.info("Credentials found. The plugin can be published from this machine.")
        }
    }
}
