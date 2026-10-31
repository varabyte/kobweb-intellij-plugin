import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    id("kobweb-compilation-settings")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.jetbrains.changelog)
}

group = "com.varabyte.kobweb.intellij"
version = libs.versions.kobweb.ide.plugin.get()

// For configuring the Gradle IntelliJ Platform Plugin, read more here:
// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html

repositories {
    google() // For androidx.compose.runtime
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    pluginVerification {
        // Stop treating OVERRIDE_ONLY_API_USAGES as a failure for now.
        // See https://platform.jetbrains.com/t/question-about-extending-psidocumentationtargetprovider/ for more
        // information about the reason.
        failureLevel = listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.INTERNAL_API_USAGES,
        )
    }
}

// Real Kobweb (JS) libraries which tests run against. `KobwebApplicationTestCase` attaches them to the test project,
// finding them via system properties set on the test task (see the bottom of this file).
val kobwebTestJsLibs = configurations.create("kobwebTestJsLibs") {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "kotlin-runtime"))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java), "js")
        attribute(Attribute.of("org.jetbrains.kotlin.js.compiler", String::class.java), "ir")
    }
}

// Real Kobweb JVM libraries for tests that also need a backend module (see `KobwebFullStackTestCase`)
val kobwebTestJvmLibs = configurations.create("kobwebTestJvmLibs") {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java), "jvm")
    }
}

dependencies {
    // `kobweb-model` is bundled as an external jar (intentionally not an `intellijPlatform.pluginModule`, which would
    // get merged into the final jar instead). Its purpose is to get injected into a running Gradle process.
    implementation(project(":kobweb-model"))
    implementation(project(":shared:icon-previews-format"))
    testImplementation(libs.truthish)
    testImplementation(kotlin("test"))
    kobwebTestJsLibs(libs.kobweb.silk)
    kobwebTestJsLibs(libs.kobweb.silk.icons.fa)
    kobwebTestJsLibs(libs.kobweb.worker)
    kobwebTestJvmLibs(libs.kobweb.api)

    intellijPlatform {
        // The version of IntelliJ IDEA we build against.
        // See all versions here: https://www.jetbrains.com/idea/download/other/
        // We generally aim to support about / at least 3 major versions back
        // NOTE: We build against 2025.3.2 (instead of 2025.3) because earlier versions crash when indexing Kobweb 0.25+
        // libraries (which tests depend on). However, the plugin declares support for all 2025.3 releases (as
        // `sinceBuild` is derived from the major release and not the patch version). We think this is OK as 1) most
        // users will have upgraded to newer patch versions anyway and 2) older versions of Kobweb don't crash.
        intellijIdea("2025.3.2")

        // See https://jb.gg/ij-plugin-dependencies
        bundledPlugins(
            "org.jetbrains.kotlin",
            "org.jetbrains.plugins.gradle",
            "com.intellij.modules.json",
            "org.jetbrains.plugins.yaml",
        )

        pluginVerifier()
        zipSigner()

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
}

intellijPlatformTesting {
    runIde {
        register("runLatestIde") {
            type = IntelliJPlatformType.IntellijIdea
            version = "latest"
        }
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
                <li>Inspections and refactoring tools for CSS styles and Modifier chains</li>
                <li>Suppressing warnings that don't apply to Kobweb projects</li>
                <li>Surfacing Kobweb colors in the gutter</li>
                <li>Extended quick docs for CSS style and HTML attribute modifiers</li>
            </ul>
            </p>
        
            <p>Source for this plugin is hosted at <a href="https://github.com/varabyte/kobweb-intellij-plugin">https://github.com/varabyte/kobweb-intellij-plugin</a></p>
            """.trimIndent()

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
        ignoredProblemsFile.set(file(".verifier/ignored-problems.txt"))
    }
}

changelog {
    path = file("../CHANGELOG.md").canonicalPath
    repositoryUrl = "https://github.com/varabyte/kobweb-intellij-plugin"
}

fun Project.isSnapshot() = version.toString().endsWith("-SNAPSHOT")

tasks.withType<Test>().configureEach {
    // Sources are needed as the plugin inspects library function bodies
    fun ResolvableDependencies.sources() = artifactView {
        // Lets this view select the sources variant of each resolved dependency (not just its klib / jar)
        @Suppress("UnstableApiUsage")
        withVariantReselection()
        lenient(true)
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType::class.java, DocsType.SOURCES))
        }
    }.files

    val jsKlibs = kobwebTestJsLibs.incoming.files
    val jsSources = kobwebTestJsLibs.incoming.sources()
    val jvmJars = kobwebTestJvmLibs.incoming.files
    val jvmSources = kobwebTestJvmLibs.incoming.sources()

    // Golden files for `WorkerClassSynthesizerParityTest` (see the README next to them)
    val workerCases = layout.projectDirectory.dir("src/test/testData/worker/cases")
    inputs.files(jsKlibs, jsSources, jvmJars, jvmSources)
    inputs.dir(workerCases).withPathSensitivity(PathSensitivity.RELATIVE)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Dkobweb.test.js.klibs=${jsKlibs.files.joinToString(File.pathSeparator)}",
            "-Dkobweb.test.js.sources=${jsSources.files.joinToString(File.pathSeparator)}",
            "-Dkobweb.test.jvm.jars=${jvmJars.files.joinToString(File.pathSeparator)}",
            "-Dkobweb.test.jvm.sources=${jvmSources.files.joinToString(File.pathSeparator)}",
            "-Dkobweb.test.worker.cases=${workerCases.asFile}",
        )
    })
}

// By default, icon previews are downloaded from the varabyte/data repo. Pass `-PlocalIconPreviews` to use data generated
// locally by `./gradlew :tools:icon-previews-generator:generate` instead, e.g. to check changes to the generator before
// publishing them.
if (providers.gradleProperty("localIconPreviews").isPresent) {
    tasks.withType<RunIdeTask>().configureEach {
        val iconPreviewsDir = layout.settingsDirectory
            .dir("tools/icon-previews-generator/build/icon-previews/data").asFile
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Dkobweb.iconPreviews.dir=$iconPreviewsDir")
        })
    }
}

// By default, import migrations are downloaded from the varabyte/data repo. Pass `-PlocalImportMigrations=<path>` to use
// a local copy of the file instead, e.g. to check changes to it before publishing them.
providers.gradleProperty("localImportMigrations").orNull?.let { importMigrationsPath ->
    tasks.withType<RunIdeTask>().configureEach {
        val importMigrationsFile = file(importMigrationsPath)
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Dkobweb.importMigrations.file=$importMigrationsFile")
        })
    }
}
