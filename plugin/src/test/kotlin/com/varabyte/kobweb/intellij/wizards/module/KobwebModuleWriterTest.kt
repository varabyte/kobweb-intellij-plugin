@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.wizards.module

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.varabyte.truthish.assertThat

class KobwebModuleWriterTest : BasePlatformTestCase() {
    private fun writeLibrary(rootDir: VirtualFile, moduleDir: String, options: KobwebLibraryOptions) {
        WriteCommandAction.runWriteCommandAction(project) {
            KobwebModuleWriter(project, rootDir).writeLibrary(moduleDir, options)
        }
    }

    private fun exists(path: String) = myFixture.findFileInTempDir(path) != null

    /** The text of a file, including changes that haven't been saved to disk yet. */
    private fun textOf(path: String): String {
        val file = myFixture.findFileInTempDir(path) ?: error("No file at $path")
        return FileDocumentManager.getInstance().getDocument(file)!!.text
    }

    fun testLibraryIsAddedToKobwebBuild() {
        val rootDir = myFixture.addFileToProject(
            "settings.gradle.kts",
            """
            rootProject.name = "mysite"

            include(":site")
            """.trimIndent()
        ).virtualFile.parent
        myFixture.addFileToProject(
            "gradle/libs.versions.toml",
            """
            [versions]
            kobweb = "0.24.0"

            [libraries]
            core = { module = "com.varabyte.kobweb:kobweb-core", version.ref = "kobweb" }

            [plugins]
            kobweb-application = { id = "com.varabyte.kobweb.application", version.ref = "kobweb" }
            """.trimIndent()
        )

        writeLibrary(
            rootDir,
            "mylib",
            KobwebLibraryOptions(
                group = "com.example.mysite.mylib",
                useSilk = true,
                useServer = true,
                useMarkdown = false,
                generateReadme = true,
            ),
        )

        assertThat(textOf("mylib/build.gradle.kts")).isEqualTo(
            """
            import com.varabyte.kobweb.gradle.library.util.configAsKobwebLibrary

            plugins {
                alias(libs.plugins.kotlin.multiplatform)
                alias(libs.plugins.compose.compiler)
                alias(libs.plugins.kobweb.library)
                // alias(libs.plugins.kobwebx.markdown)
            }

            group = "com.example.mysite.mylib"
            version = "1.0-SNAPSHOT"

            kotlin {
                configAsKobwebLibrary(includeServer = true)

                sourceSets {
            //        commonMain.dependencies {
            //          // Add shared dependencies between JS and JVM here
            //        }
                    jsMain.dependencies {
                        implementation(libs.compose.runtime)
                        implementation(libs.compose.html.core)
                        implementation(libs.core)
                        implementation(libs.kobweb.silk)
                        // implementation(libs.kobwebx.markdown)
                    }
                    jvmMain.dependencies {
                        compileOnly(libs.kobweb.api) // Provided by Kobweb backend at runtime
                    }
                }
            }

            """.trimIndent()
        )
        assertThat(textOf("settings.gradle.kts")).isEqualTo(
            """
            rootProject.name = "mysite"

            include(":site")
            include(":mylib")

            """.trimIndent()
        )
        // The existing Kobweb version is kept, and `core` is reused
        assertThat(textOf("gradle/libs.versions.toml")).isEqualTo(
            """
            [versions]
            kobweb = "0.24.0"
            compose-runtime = "1.12.0"
            compose-html = "1.11.1"
            kotlin = "2.4.10"

            [libraries]
            core = { module = "com.varabyte.kobweb:kobweb-core", version.ref = "kobweb" }
            compose-runtime = { group = "androidx.compose.runtime", name = "runtime", version.ref = "compose-runtime" }
            compose-html-core = { group = "org.jetbrains.compose.html", name = "html-core", version.ref = "compose-html" }
            kobweb-silk = { group = "com.varabyte.kobweb", name = "kobweb-silk", version.ref = "kobweb" }
            kobwebx-markdown = { group = "com.varabyte.kobwebx", name = "kobwebx-markdown", version.ref = "kobweb" }
            kobweb-api = { group = "com.varabyte.kobweb", name = "kobweb-api", version.ref = "kobweb" }

            [plugins]
            kobweb-application = { id = "com.varabyte.kobweb.application", version.ref = "kobweb" }
            kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
            compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
            kobweb-library = { id = "com.varabyte.kobweb.library", version.ref = "kobweb" }
            kobwebx-markdown = { id = "com.varabyte.kobwebx.markdown", version.ref = "kobweb" }
            """.trimIndent()
        )
        assertThat(textOf("mylib/README.md")).isEqualTo(
            """
            # mylib

            A Kobweb library. Its code goes in these folders:

            * `src/jsMain/kotlin/com/example/mysite/mylib/components`: layouts, sections, and widgets
              (see https://kobweb.varabyte.com/docs/concepts/foundation/project-structure#components-and-pages)
            * `src/jsMain/kotlin/com/example/mysite/mylib/pages`: pages
              (see https://kobweb.varabyte.com/docs/concepts/foundation/routing#page)
            * `src/jvmMain/kotlin/com/example/mysite/mylib/api`: API routes
              (see https://kobweb.varabyte.com/docs/concepts/server/fullstack#define-api-routes)

            """.trimIndent()
        )
        assertThat(exists("mylib/src/jsMain/kotlin/com/example/mysite/mylib/components")).isTrue()
        assertThat(exists("mylib/src/jsMain/kotlin/com/example/mysite/mylib/pages")).isTrue()
        assertThat(exists("mylib/src/jsMain/resources")).isTrue()
        assertThat(exists("mylib/src/jsMain/resources/markdown")).isFalse()
        assertThat(exists("mylib/src/jvmMain/kotlin/com/example/mysite/mylib/api")).isTrue()
    }

    fun testLibraryWithoutServerOrSilk() {
        val rootDir = myFixture.addFileToProject("settings.gradle.kts", "rootProject.name = \"mysite\"")
            .virtualFile.parent

        writeLibrary(
            rootDir,
            "mylib",
            KobwebLibraryOptions(
                group = "com.example.mylib",
                useSilk = false,
                useServer = false,
                useMarkdown = true,
                generateReadme = true,
            ),
        )

        assertThat(textOf("mylib/build.gradle.kts")).isEqualTo(
            """
            import com.varabyte.kobweb.gradle.library.util.configAsKobwebLibrary

            plugins {
                alias(libs.plugins.kotlin.multiplatform)
                alias(libs.plugins.compose.compiler)
                alias(libs.plugins.kobweb.library)
                alias(libs.plugins.kobwebx.markdown)
            }

            group = "com.example.mylib"
            version = "1.0-SNAPSHOT"

            kotlin {
                configAsKobwebLibrary()

                sourceSets {
                    jsMain.dependencies {
                        implementation(libs.compose.runtime)
                        implementation(libs.compose.html.core)
                        implementation(libs.kobweb.core)
                        // implementation(libs.kobweb.silk)
                        implementation(libs.kobwebx.markdown)
                    }
                }
            }

            """.trimIndent()
        )
        assertThat(textOf("mylib/README.md")).isEqualTo(
            """
            # mylib

            A Kobweb library. Its code goes in these folders:

            * `src/jsMain/kotlin/com/example/mylib/components`: layouts, sections, and widgets
              (see https://kobweb.varabyte.com/docs/concepts/foundation/project-structure#components-and-pages)
            * `src/jsMain/kotlin/com/example/mylib/pages`: pages
              (see https://kobweb.varabyte.com/docs/concepts/foundation/routing#page)
            * `src/jsMain/resources/markdown`: Markdown files, which get converted into pages
              (see https://kobweb.varabyte.com/docs/concepts/foundation/markdown)

            """.trimIndent()
        )
        assertThat(exists("mylib/src/jsMain/resources/markdown")).isTrue()
        assertThat(exists("mylib/src/jvmMain")).isFalse()
    }

    fun testMissingCatalogIsCreatedFromTheTemplateCatalog() {
        val rootDir = myFixture.addFileToProject("settings.gradle.kts", "rootProject.name = \"mysite\"")
            .virtualFile.parent

        writeLibrary(
            rootDir,
            "mylib",
            KobwebLibraryOptions(
                group = "com.example.mylib",
                useSilk = true,
                useServer = false,
                useMarkdown = false,
                generateReadme = true,
            ),
        )

        val templateCatalogText =
            KobwebModuleWriter::class.java.getResource("/kobweb/templates/libs.versions.toml")!!.readText()
        assertThat(textOf("gradle/libs.versions.toml")).isEqualTo(templateCatalogText)
    }

    fun testNestedModuleDirectoryGetsNestedGradlePath() {
        val rootDir = myFixture.addFileToProject("settings.gradle.kts", "rootProject.name = \"mysite\"")
            .virtualFile.parent

        writeLibrary(
            rootDir,
            "libs/mylib",
            KobwebLibraryOptions(
                group = "com.example.mylib",
                useSilk = true,
                useServer = false,
                useMarkdown = false,
                generateReadme = true,
            ),
        )

        assertThat(textOf("settings.gradle.kts")).isEqualTo(
            """
            rootProject.name = "mysite"
            include(":libs:mylib")

            """.trimIndent()
        )
        assertThat(exists("libs/mylib/build.gradle.kts")).isTrue()
    }

    fun testReadmeIsOptional() {
        val rootDir = myFixture.addFileToProject("settings.gradle.kts", "rootProject.name = \"mysite\"")
            .virtualFile.parent

        writeLibrary(
            rootDir,
            "mylib",
            KobwebLibraryOptions(
                group = "com.example.mylib",
                useSilk = true,
                useServer = false,
                useMarkdown = false,
                generateReadme = false,
            ),
        )

        assertThat(exists("mylib/README.md")).isFalse()
        assertThat(exists("mylib/src/jsMain/kotlin/com/example/mylib/pages")).isTrue()
    }
}
