@file:Suppress("FunctionName", "SameParameterValue")

package com.varabyte.kobweb.intellij.wizards.module

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.varabyte.truthish.assertThat

class KobwebModuleWriterTest : BasePlatformTestCase() {
    private fun writeApp(rootDir: VirtualFile, moduleDir: String, options: KobwebAppOptions) {
        WriteCommandAction.runWriteCommandAction(project) {
            KobwebModuleWriter(project, rootDir).writeApp(moduleDir, options)
        }
    }

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

    fun testAppWithSilkMarkdownAndSampleCode() {
        val rootDir = myFixture.addFileToProject("settings.gradle.kts", "rootProject.name = \"mysite\"")
            .virtualFile.parent

        writeApp(
            rootDir,
            "site",
            KobwebAppOptions(
                group = "com.example.mysite",
                title = "My Site",
                useSilk = true,
                useServer = false,
                useMarkdown = true,
                generateReadme = true,
                addSampleCode = true,
            ),
        )

        assertThat(textOf("site/build.gradle.kts")).isEqualTo(
            """
            import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication

            plugins {
                alias(libs.plugins.kotlin.multiplatform)
                alias(libs.plugins.compose.compiler)
                alias(libs.plugins.kobweb.application)
                alias(libs.plugins.kobwebx.markdown)
            }

            group = "com.example.mysite"
            version = "1.0-SNAPSHOT"

            kobweb {
                app {
                    index {
                        description.set("Powered by Kobweb")
                    }
                }
            }

            kotlin {
                configAsKobwebApplication("mysite")

                sourceSets {
                    jsMain.dependencies {
                        implementation(libs.compose.runtime)
                        implementation(libs.compose.html.core)
                        implementation(libs.kobweb.core)
                        implementation(libs.kobweb.silk)
                        implementation(libs.silk.icons.fa)
                        implementation(libs.kobwebx.markdown)
                    }
                }
            }

            """.trimIndent()
        )
        assertThat(textOf("site/.kobweb/conf.yaml")).isEqualTo(
            """
            site:
              title: "My Site"

            server:
              files:
                dev:
                  contentRoot: "build/processedResources/js/main/public"
                  script: "build/kotlin-webpack/js/developmentExecutable/mysite.js"
                  api: "build/libs/mysite.jar"
                prod:
                  script: "build/kotlin-webpack/js/productionExecutable/mysite.js"
                  siteRoot: ".kobweb/site"

              port: 8080

            """.trimIndent()
        )
        assertThat(textOf("site/.gitignore")).isEqualTo(
            """
            # Kobweb ignores
            .kobweb/*
            !.kobweb/conf.yaml

            """.trimIndent()
        )
        assertThat(textOf("site/src/jsMain/kotlin/com/example/mysite/AppEntry.kt")).isEqualTo(
            """
            package com.example.mysite

            import androidx.compose.runtime.*
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.core.App
            import com.varabyte.kobweb.silk.SilkApp
            import com.varabyte.kobweb.silk.components.layout.Surface
            import com.varabyte.kobweb.silk.init.InitSilk
            import com.varabyte.kobweb.silk.init.InitSilkContext
            import com.varabyte.kobweb.silk.init.registerStyleBase
            import com.varabyte.kobweb.silk.style.common.SmoothColorStyle
            import com.varabyte.kobweb.silk.style.toModifier

            @InitSilk
            fun initStyles(ctx: InitSilkContext) {
                ctx.stylesheet.registerStyleBase("html, body") { Modifier.fillMaxHeight() }
            }

            @App
            @Composable
            fun AppEntry(content: @Composable () -> Unit) {
                SilkApp {
                    Surface(SmoothColorStyle.toModifier().fillMaxHeight()) {
                        content()
                    }
                }
            }

            """.trimIndent()
        )
        assertThat(textOf("site/src/jsMain/kotlin/com/example/mysite/components/layouts/PageLayout.kt")).isEqualTo(
            """
            package com.example.mysite.components.layouts

            import androidx.compose.runtime.*
            import com.varabyte.kobweb.compose.foundation.layout.Column
            import com.varabyte.kobweb.compose.ui.Alignment
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.modifiers.*
            import com.varabyte.kobweb.core.layout.Layout
            import org.jetbrains.compose.web.css.cssRem

            @Layout
            @Composable
            fun PageLayout(content: @Composable () -> Unit) {
                // TODO: Add the parts that all your pages share, like a header and a footer
                Column(
                    Modifier.fillMaxSize().padding(2.cssRem),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    content()
                }
            }

            """.trimIndent()
        )
        assertThat(textOf("site/src/jsMain/kotlin/com/example/mysite/pages/Index.kt")).isEqualTo(
            """
            package com.example.mysite.pages

            import androidx.compose.runtime.*
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout
            import com.varabyte.kobweb.silk.components.navigation.Link
            import org.jetbrains.compose.web.dom.P
            import org.jetbrains.compose.web.dom.Text

            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun HomePage() {
                // TODO: Replace the following with your own content
                P { Text("THIS PAGE INTENTIONALLY LEFT BLANK") }
                Link("/about", "About")
            }

            """.trimIndent()
        )
        assertThat(textOf("site/src/jsMain/resources/markdown/About.md")).isEqualTo(
            """
            ---
            title: About
            layout: .components.layouts.PageLayout
            ---

            # About

            This page is written in Markdown. Kobweb converts it into a page at `/about`.

            """.trimIndent()
        )
        assertThat(textOf("site/README.md")).isEqualTo(
            """
            # site

            A Kobweb application. To run it, open a terminal in this folder and type `kobweb run`, then visit http://localhost:8080.
            (See https://kobweb.varabyte.com/docs/getting-started/getting-kobweb for how to install the `kobweb` command.)

            Its code goes in these places:

            * `src/jsMain/kotlin/com/example/mysite/AppEntry.kt`: the `@App` function that wraps every page
            * `src/jsMain/kotlin/com/example/mysite/components`: layouts, sections, and widgets
              (see https://kobweb.varabyte.com/docs/concepts/foundation/project-structure#components-and-pages)
            * `src/jsMain/kotlin/com/example/mysite/pages`: pages
              (see https://kobweb.varabyte.com/docs/concepts/foundation/routing#page)
            * `src/jsMain/resources/public`: files served as is, like images and the favicon
            * `src/jsMain/resources/markdown`: Markdown files, which get converted into pages
              (see https://kobweb.varabyte.com/docs/concepts/foundation/markdown)
            * `.kobweb/conf.yaml`: the site's title and server settings

            """.trimIndent()
        )
        assertThat(exists("site/src/jsMain/resources/public/favicon.ico")).isTrue()
        assertThat(exists("site/src/jsMain/kotlin/com/example/mysite/components")).isTrue()
        assertThat(exists("site/src/jvmMain")).isFalse()
        assertThat(textOf("settings.gradle.kts")).isEqualTo(
            """
            rootProject.name = "mysite"
            include(":site")

            """.trimIndent()
        )
    }

    fun testAppSampleCodeWithoutSilk() {
        val rootDir = myFixture.addFileToProject("settings.gradle.kts", "rootProject.name = \"mysite\"")
            .virtualFile.parent

        writeApp(
            rootDir,
            "site",
            KobwebAppOptions(
                group = "com.example.mysite",
                title = "My Site",
                useSilk = false,
                useServer = false,
                useMarkdown = true,
                generateReadme = false,
                addSampleCode = true,
            ),
        )

        assertThat(textOf("site/src/jsMain/kotlin/com/example/mysite/components/layouts/PageLayout.kt")).isEqualTo(
            """
            package com.example.mysite.components.layouts

            import androidx.compose.runtime.*
            import com.varabyte.kobweb.core.layout.Layout
            import org.jetbrains.compose.web.css.*
            import org.jetbrains.compose.web.dom.Div

            @Layout
            @Composable
            fun PageLayout(content: @Composable () -> Unit) {
                // TODO: Add the parts that all your pages share, like a header and a footer
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        alignItems(AlignItems.Center)
                        minHeight(100.vh)
                        padding(2.cssRem)
                    }
                }) {
                    content()
                }
            }

            """.trimIndent()
        )
        assertThat(textOf("site/src/jsMain/kotlin/com/example/mysite/pages/Index.kt")).isEqualTo(
            """
            package com.example.mysite.pages

            import androidx.compose.runtime.*
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout
            import com.varabyte.kobweb.navigation.Anchor
            import org.jetbrains.compose.web.dom.P
            import org.jetbrains.compose.web.dom.Text

            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun HomePage() {
                // TODO: Replace the following with your own content
                P { Text("THIS PAGE INTENTIONALLY LEFT BLANK") }
                Anchor("/about") { Text("About") }
            }

            """.trimIndent()
        )
    }

    fun testAppWithServerButWithoutSilkMarkdownOrSampleCode() {
        val rootDir = myFixture.addFileToProject("settings.gradle.kts", "rootProject.name = \"mysite\"")
            .virtualFile.parent

        writeApp(
            rootDir,
            "site",
            KobwebAppOptions(
                group = "com.example.mysite",
                title = "My Site",
                useSilk = false,
                useServer = true,
                useMarkdown = false,
                generateReadme = false,
                addSampleCode = false,
            ),
        )

        assertThat(textOf("site/build.gradle.kts")).isEqualTo(
            """
            import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication

            plugins {
                alias(libs.plugins.kotlin.multiplatform)
                alias(libs.plugins.compose.compiler)
                alias(libs.plugins.kobweb.application)
                // alias(libs.plugins.kobwebx.markdown)
            }

            group = "com.example.mysite"
            version = "1.0-SNAPSHOT"

            kobweb {
                app {
                    index {
                        description.set("Powered by Kobweb")
                    }
                }
            }

            kotlin {
                configAsKobwebApplication("mysite", includeServer = true)

                sourceSets {
            //        commonMain.dependencies {
            //          // Add shared dependencies between JS and JVM here
            //        }
                    jsMain.dependencies {
                        implementation(libs.compose.runtime)
                        implementation(libs.compose.html.core)
                        implementation(libs.kobweb.core)
                        // implementation(libs.kobweb.silk)
                        // implementation(libs.silk.icons.fa)
                        // implementation(libs.kobwebx.markdown)
                    }
                    jvmMain.dependencies {
                        compileOnly(libs.kobweb.api) // Provided by Kobweb backend at runtime
                    }
                }
            }

            """.trimIndent()
        )
        assertThat(textOf("site/src/jsMain/kotlin/com/example/mysite/AppEntry.kt")).isEqualTo(
            """
            package com.example.mysite

            import androidx.compose.runtime.*
            import com.varabyte.kobweb.core.App
            import com.varabyte.kobweb.core.KobwebApp

            @App
            @Composable
            fun AppEntry(content: @Composable () -> Unit) {
                KobwebApp {
                    content()
                }
            }

            """.trimIndent()
        )
        assertThat(exists("site/src/jsMain/kotlin/com/example/mysite/pages/Index.kt")).isFalse()
        assertThat(exists("site/src/jsMain/kotlin/com/example/mysite/pages")).isTrue()
        assertThat(exists("site/src/jsMain/resources/public/favicon.ico")).isFalse()
        assertThat(exists("site/src/jsMain/resources/markdown")).isFalse()
        assertThat(exists("site/src/jvmMain/kotlin/com/example/mysite/api")).isTrue()
        assertThat(exists("site/README.md")).isFalse()
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
                addSampleCode = false,
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
                addSampleCode = false,
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

    fun testLibrarySampleCode() {
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
                addSampleCode = true,
            ),
        )

        assertThat(textOf("mylib/src/jsMain/kotlin/com/example/mylib/pages/Example.kt")).isEqualTo(
            """
            package com.example.mylib.pages

            import androidx.compose.runtime.*
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout
            import org.jetbrains.compose.web.dom.P
            import org.jetbrains.compose.web.dom.Text

            // Sites that depend on this library get this page at `/example`
            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun ExamplePage() {
                // TODO: Replace the following with your own content
                P { Text("This page comes from the mylib library.") }
            }

            """.trimIndent()
        )
        // The same layout as an app's sample code, which the app test checks in full
        assertThat(exists("mylib/src/jsMain/kotlin/com/example/mylib/components/layouts/PageLayout.kt")).isTrue()
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
                addSampleCode = false,
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
                addSampleCode = false,
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
                addSampleCode = false,
            ),
        )

        assertThat(exists("mylib/README.md")).isFalse()
        assertThat(exists("mylib/src/jsMain/kotlin/com/example/mylib/pages")).isTrue()
    }
}
