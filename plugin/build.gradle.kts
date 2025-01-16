import org.jetbrains.changelog.Changelog
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.jetbrains.changelog)
}

group = "com.varabyte.kobweb.intellij"
version = libs.versions.kobweb.ide.plugin.get()

// For configuring the Gradle IntelliJ Platform Plugin, read more here:
// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // `kobweb-model` is bundled as an external jar (intentionally not an `intellijPlatform.pluginModule`, which would
    // get merged into the final jar instead). Its purpose is to get injected into a running Gradle process.
    implementation(project(":kobweb-model"))
    testImplementation(libs.truthish)

    intellijPlatform {
        // Interesting statistics: https://plugins.jetbrains.com/docs/marketplace/product-versions-in-use-statistics.html
        // We target 2024.2.1 as it is the earliest version supporting K2 mode / the Analysis API
        intellijIdeaCommunity("2024.2.1")

        bundledPlugins(
            "org.jetbrains.kotlin",
            "org.jetbrains.plugins.gradle",
        )

        pluginVerifier()
        zipSigner()
    }
}

// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform
intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        id = "com.varabyte.kobweb"
        name = "Kobweb"
        vendor {
            url = "https://kobweb.varabyte.com"
            email = "bitspittle@gmail.com"
            name = "Varabyte"
        }
        description =
            """
            <p>Support for the <a href="https://github.com/varabyte/kobweb">Kobweb</a> framework.</p>
        
            <p>
            This official plugin provides functionality relevant to users working on Kobweb projects, including:
            <ul>
                <li>Suppressing warnings that don't apply to Kobweb projects</li>
                <li>Surfacing Kobweb colors in the gutter</li>
                <li>(More to come very soon!)</li>
            </ul>
            </p>
        
            <p>Source for this plugin is hosted at <a href="https://github.com/varabyte/kobweb-intellij-plugin">https://github.com/varabyte/kobweb-intellij-plugin</a></p>
            """.trimIndent()

        ideaVersion {
            //sinceBuild derived from intellij.version
            untilBuild = "251.*" // Include EAP
        }

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
        signing {
            val password = (findProperty("kobweb.intellij.plugin.password") as? String) ?: return@signing
            val key = (findProperty("kobweb.intellij.plugin.key") as? String) ?: return@signing
            val cert = (findProperty("kobweb.intellij.plugin.cert") as? String) ?: return@signing
            credentialsFound = true

            this.password = password
            this.privateKey = key
            this.certificateChain = cert
        }

        if (!credentialsFound) {
            logger.lifecycle("Signing credentials not found. The plugin cannot be signed on this machine.")
        } else {
            logger.lifecycle("Signing credentials found. The plugin can be signed on this machine.")
        }
    }

    run {
        var tokenFound = false
        publishing {
            val token = (findProperty("kobweb.intellij.plugin.publish.token") as? String) ?: return@publishing
            tokenFound = true
            this.token = token

            if (project.isSnapshot()) {
                channels = listOf("eap")
            }
        }

        if (!tokenFound) {
            logger.lifecycle("Publishing credentials not found. The plugin cannot be published from this machine.")
        } else {
            logger.lifecycle("Publishing credentials found. The plugin can be published from this machine.")
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

changelog {
    path = file("../CHANGELOG.md").canonicalPath
    repositoryUrl = "https://github.com/varabyte/kobweb-intellij-plugin"
}

fun Project.isSnapshot() = version.toString().endsWith("-SNAPSHOT")

val jvmTarget = JvmTarget.JVM_21
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = jvmTarget.target
    targetCompatibility = jvmTarget.target
}
kotlin.compilerOptions.jvmTarget = jvmTarget
