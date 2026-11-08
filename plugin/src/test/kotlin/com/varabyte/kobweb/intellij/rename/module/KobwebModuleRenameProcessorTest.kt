@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.rename.module

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.psi.PsiManager
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService
import com.varabyte.kobweb.intellij.util.kobweb.KobwebPluginState
import com.varabyte.kobweb.intellij.util.kobweb.kobwebPluginState
import com.varabyte.truthish.assertThat
import org.jetbrains.plugins.gradle.util.GradleConstants

class KobwebModuleRenameProcessorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        project.kobwebPluginState = KobwebPluginState.INITIALIZED
    }

    override fun tearDown() {
        try {
            WriteAction.runAndWait<Throwable> { ExternalSystemModulePropertyManager.getInstance(module).unlinkExternalOptions() }
            project.service<KobwebProjectCacheService>().clear()
            project.kobwebPluginState = KobwebPluginState.DISABLED
        } finally {
            super.tearDown()
        }
    }

    /**
     * Makes the test's module the Kobweb module in [dir] with the given Gradle path, as a Gradle sync would. The root of
     * the Gradle build is the folder with the settings script.
     */
    private fun registerKobwebModule(dir: String, gradlePath: String, type: KobwebProjectType) {
        val dirPath = myFixture.findFileInTempDir(dir)!!.path
        val rootPath = myFixture.findFileInTempDir("settings.gradle.kts")!!.parent.path
        WriteAction.runAndWait<Throwable> {
            ExternalSystemModulePropertyManager.getInstance(module).setExternalOptions(
                GradleConstants.SYSTEM_ID,
                ModuleData(gradlePath, GradleConstants.SYSTEM_ID, "JAVA_MODULE", dir, dirPath, dirPath),
                ProjectData(GradleConstants.SYSTEM_ID, "root", rootPath, rootPath),
            )
        }
        project.service<KobwebProjectCacheService>().add(module, KobwebProject(dir, type, KobwebProject.Source.Local(module)))
    }

    private fun renameDir(path: String, newName: String) {
        val dir = PsiManager.getInstance(project).findDirectory(myFixture.findFileInTempDir(path)!!)!!
        myFixture.renameElement(dir, newName)
    }

    private fun exists(path: String) = myFixture.findFileInTempDir(path) != null

    /** The text of a file, including changes that haven't been saved to disk yet. */
    private fun textOf(path: String): String {
        val file = myFixture.findFileInTempDir(path) ?: error("No file at $path")
        return FileDocumentManager.getInstance().getDocument(file)!!.text
    }

    fun testRenamingApplicationRenamesIncludeAndApplicationName() {
        myFixture.addFileToProject(
            "settings.gradle.kts",
            """
            rootProject.name = "myproject"

            include(":site")
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "site/build.gradle.kts",
            """
            import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication

            kotlin {
                configAsKobwebApplication("site", includeServer = true)
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "site/.kobweb/conf.yaml",
            """
            server:
              files:
                dev:
                  contentRoot: "build/processedResources/js/main/public"
                  script: "build/kotlin-webpack/js/developmentExecutable/site.js"
                  api: "build/libs/site.jar"
                prod:
                  script: "build/kotlin-webpack/js/productionExecutable/site.js"
                  siteRoot: ".kobweb/site"
            """.trimIndent(),
        )

        registerKobwebModule("site", ":site", KobwebProjectType.Application)

        renameDir("site", "website")

        assertThat(exists("site")).isFalse()
        assertThat(textOf("settings.gradle.kts")).isEqualTo(
            """
            rootProject.name = "myproject"

            include(":website")
            """.trimIndent(),
        )
        assertThat(textOf("website/build.gradle.kts")).isEqualTo(
            """
            import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication

            kotlin {
                configAsKobwebApplication("website", includeServer = true)
            }
            """.trimIndent(),
        )
        assertThat(textOf("website/.kobweb/conf.yaml")).isEqualTo(
            """
            server:
              files:
                dev:
                  contentRoot: "build/processedResources/js/main/public"
                  script: "build/kotlin-webpack/js/developmentExecutable/website.js"
                  api: "build/libs/website.jar"
                prod:
                  script: "build/kotlin-webpack/js/productionExecutable/website.js"
                  siteRoot: ".kobweb/site"
            """.trimIndent(),
        )
    }

    fun testRenamingApplicationKeepsNameNotTakenFromFolder() {
        myFixture.addFileToProject(
            "settings.gradle.kts",
            """
            rootProject.name = "myproject"

            include(":site")
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "site/build.gradle.kts",
            """
            kotlin {
                configAsKobwebApplication("myproject")
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "site/.kobweb/conf.yaml",
            """
            server:
              files:
                dev:
                  script: "build/kotlin-webpack/js/developmentExecutable/myproject.js"
            """.trimIndent(),
        )

        registerKobwebModule("site", ":site", KobwebProjectType.Application)

        renameDir("site", "website")

        assertThat(textOf("settings.gradle.kts")).isEqualTo(
            """
            rootProject.name = "myproject"

            include(":website")
            """.trimIndent(),
        )
        assertThat(textOf("website/build.gradle.kts")).isEqualTo(
            """
            kotlin {
                configAsKobwebApplication("myproject")
            }
            """.trimIndent(),
        )
        assertThat(textOf("website/.kobweb/conf.yaml")).isEqualTo(
            """
            server:
              files:
                dev:
                  script: "build/kotlin-webpack/js/developmentExecutable/myproject.js"
            """.trimIndent(),
        )
    }

    fun testRenamingNestedLibrary() {
        myFixture.addFileToProject(
            "settings.gradle.kts",
            """
            include(":site")
            include(":libs:mylib")
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "libs/mylib/build.gradle.kts",
            """
            kotlin {
                configAsKobwebLibrary()
            }
            """.trimIndent(),
        )

        registerKobwebModule("libs/mylib", ":libs:mylib", KobwebProjectType.Library)

        renameDir("libs/mylib", "widgets")

        assertThat(exists("libs/widgets/build.gradle.kts")).isTrue()
        assertThat(textOf("settings.gradle.kts")).isEqualTo(
            """
            include(":site")
            include(":libs:widgets")
            """.trimIndent(),
        )
    }

    fun testRenamingWorkerKeepsScriptName() {
        myFixture.addFileToProject(
            "settings.gradle.kts",
            """
            include(":site")
            include(":worker")
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "worker/build.gradle.kts",
            """
            kotlin {
                configAsKobwebWorker("echo-worker")
            }
            """.trimIndent(),
        )

        registerKobwebModule("worker", ":worker", KobwebProjectType.Worker)

        renameDir("worker", "echo")

        assertThat(textOf("settings.gradle.kts")).isEqualTo(
            """
            include(":site")
            include(":echo")
            """.trimIndent(),
        )
        assertThat(textOf("echo/build.gradle.kts")).isEqualTo(
            """
            kotlin {
                configAsKobwebWorker("echo-worker")
            }
            """.trimIndent(),
        )
    }

    // Selecting a module's folder in the Project view offers both "Rename directory" and "Rename module"
    fun testRenameModuleOptionRenamesFolder() {
        myFixture.addFileToProject("settings.gradle.kts", "include(\":site\")")
        myFixture.addFileToProject(
            "site/build.gradle.kts",
            """
            kotlin {
                configAsKobwebApplication("site")
            }
            """.trimIndent(),
        )
        registerKobwebModule("site", ":site", KobwebProjectType.Application)

        val dir = PsiManager.getInstance(project).findDirectory(myFixture.findFileInTempDir("site")!!)!!
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.PSI_ELEMENT, dir)
            .add(LangDataKeys.MODULE_CONTEXT, module)
            .add(PsiElementRenameHandler.DEFAULT_NAME, "website")
            .build()
        val handler = RenameHandlerRegistry.getInstance().getRenameHandlers(dataContext)
            .single { UIUtil.removeMnemonic(RenameHandlerRegistry.getHandlerTitle(it)) == "Rename module" }

        TestDialogManager.setTestInputDialog { "website" }
        try {
            handler.invoke(project, arrayOf(dir), dataContext)
        } finally {
            TestDialogManager.setTestInputDialog(TestInputDialog.DEFAULT)
        }

        assertThat(exists("website/build.gradle.kts")).isTrue()
        assertThat(textOf("settings.gradle.kts")).isEqualTo("include(\":website\")")
    }

    fun testRenamingWorkerNamedAfterFolderRenamesItsName() {
        myFixture.addFileToProject("settings.gradle.kts", "include(\":worker\")")
        myFixture.addFileToProject(
            "worker/build.gradle.kts",
            """
            kotlin {
                configAsKobwebWorker("worker")
            }
            """.trimIndent(),
        )

        registerKobwebModule("worker", ":worker", KobwebProjectType.Worker)

        renameDir("worker", "echo")

        assertThat(textOf("settings.gradle.kts")).isEqualTo("include(\":echo\")")
        assertThat(textOf("echo/build.gradle.kts")).isEqualTo(
            """
            kotlin {
                configAsKobwebWorker("echo")
            }
            """.trimIndent(),
        )
    }

    fun testRenamingNonKobwebModuleOnlyRenamesFolder() {
        myFixture.addFileToProject("settings.gradle.kts", "include(\":backend\")")
        myFixture.addFileToProject("backend/build.gradle.kts", "plugins { kotlin(\"jvm\") }")

        renameDir("backend", "server")

        assertThat(exists("server/build.gradle.kts")).isTrue()
        assertThat(textOf("settings.gradle.kts")).isEqualTo("include(\":backend\")")
    }

    // IntelliJ's own directory rename updates `file("web")`, but the Gradle path and the application name aren't
    // related to the folder's name
    fun testRenamingModuleWithCustomProjectDirKeepsGradlePath() {
        myFixture.addFileToProject(
            "settings.gradle.kts",
            """
            include(":site")
            project(":site").projectDir = file("web")
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "web/build.gradle.kts",
            """
            kotlin {
                configAsKobwebApplication("site")
            }
            """.trimIndent(),
        )

        registerKobwebModule("web", ":site", KobwebProjectType.Application)

        renameDir("web", "website")

        assertThat(textOf("settings.gradle.kts")).isEqualTo(
            """
            include(":site")
            project(":site").projectDir = file("website")
            """.trimIndent(),
        )
        assertThat(textOf("website/build.gradle.kts")).isEqualTo(
            """
            kotlin {
                configAsKobwebApplication("site")
            }
            """.trimIndent(),
        )
    }
}
