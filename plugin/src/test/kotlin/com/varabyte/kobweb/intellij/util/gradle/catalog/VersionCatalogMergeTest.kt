@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.util.gradle.catalog

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests merging a "template" catalog (which the plugin provides) into a "user" catalog (the one in the user's project).
 *
 * The user catalog is only ever added to. Entries it already has are kept as is, and when the two catalogs disagree
 * (e.g. on an alias or a Kobweb version), the user catalog wins.
 *
 * All test names are written assuming the condition being asserted applies to the user catalog.
 */
class VersionCatalogMergeTest : BasePlatformTestCase() {
    private fun PsiFile.toCatalog() =
        VersionCatalog(ProjectBuildModel.get(project).versionCatalogsModel.getVersionCatalogModel(virtualFile, "libs"))

    private fun configureUserCatalog(text: String) =
        myFixture.configureByText("libs.versions.toml", text.trimIndent()).toCatalog()

    private fun addTemplateCatalog(text: String) =
        myFixture.addFileToProject("template/libs.versions.toml", text.trimIndent()).toCatalog()

    private fun VersionCatalog.copyEntriesIfAbsentInWriteCommand(
        from: VersionCatalog,
        libraryAliases: List<String>,
        pluginAliases: List<String>,
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            copyEntriesIfAbsent(from, libraryAliases, pluginAliases)
            applyChanges()
        }
    }

    fun testEmptyCatalogGetsEverything() {
        val templateCatalog = addTemplateCatalog(
            """
            [versions]
            kobweb = "0.25.1"

            [libraries]
            kobweb-core = { module = "com.varabyte.kobweb:kobweb-core", version.ref = "kobweb" }

            [plugins]
            kobweb-library = { id = "com.varabyte.kobweb.library", version.ref = "kobweb" }
            """
        )
        val userCatalog = configureUserCatalog("")

        userCatalog.copyEntriesIfAbsentInWriteCommand(
            from = templateCatalog,
            libraryAliases = listOf("kobweb-core"),
            pluginAliases = listOf("kobweb-library"),
        )

        // Tables that the DSL creates don't get a blank line before them
        myFixture.checkResult(
            """
            [versions]
            kobweb = "0.25.1"
            [libraries]
            kobweb-core = { group = "com.varabyte.kobweb", name = "kobweb-core", version.ref = "kobweb" }
            [plugins]
            kobweb-library = { id = "com.varabyte.kobweb.library", version.ref = "kobweb" }
            """.trimIndent()
        )
    }

    fun testExistingEntriesAreMatchedByIdNotAlias() {
        val templateCatalog = addTemplateCatalog(
            """
            [versions]
            kobweb = "0.25.1"

            [libraries]
            kobweb-core = { module = "com.varabyte.kobweb:kobweb-core", version.ref = "kobweb" }
            kobweb-silk = { group = "com.varabyte.kobweb", name = "kobweb-silk", version.ref = "kobweb" }

            [plugins]
            kobweb-library = { id = "com.varabyte.kobweb.library", version.ref = "kobweb" }
            """
        )
        val userCatalogText = """
            [libraries]
            core = "com.varabyte.kobweb:kobweb-core:0.24.0"
            silk = { group = "com.varabyte.kobweb", name = "kobweb-silk", version = "0.24.0" }

            [plugins]
            kobwebLib = "com.varabyte.kobweb.library:0.24.0"
            """.trimIndent()
        val userCatalog = configureUserCatalog(userCatalogText)

        userCatalog.copyEntriesIfAbsentInWriteCommand(
            from = templateCatalog,
            libraryAliases = listOf("kobweb-core", "kobweb-silk"),
            pluginAliases = listOf("kobweb-library"),
        )

        myFixture.checkResult(userCatalogText)
    }

    fun testAddedEntriesGoAtTheEndOfTheirTable() {
        val templateCatalog = addTemplateCatalog(
            """
            [versions]
            kobweb = "0.25.1"

            [libraries]
            kobweb-silk = { module = "com.varabyte.kobweb:kobweb-silk", version.ref = "kobweb" }
            """
        )
        val userCatalog = configureUserCatalog(
            """
            [versions]
            kobweb = "0.24.0"

            [libraries]
            kobweb-core = { module = "com.varabyte.kobweb:kobweb-core", version.ref = "kobweb" }
            kotlinx-coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version = "1.10.2" }
            """
        )

        userCatalog.copyEntriesIfAbsentInWriteCommand(
            from = templateCatalog,
            libraryAliases = listOf("kobweb-silk"),
            pluginAliases = emptyList(),
        )

        myFixture.checkResult(
            """
            [versions]
            kobweb = "0.24.0"

            [libraries]
            kobweb-core = { module = "com.varabyte.kobweb:kobweb-core", version.ref = "kobweb" }
            kotlinx-coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version = "1.10.2" }
            kobweb-silk = { group = "com.varabyte.kobweb", name = "kobweb-silk", version.ref = "kobweb" }
            """.trimIndent()
        )
    }

    fun testSiblingLiteralVersionIsReusedUnderTheTemplateKey() {
        val templateCatalog = addTemplateCatalog(
            """
            [versions]
            kobweb = "0.25.1"

            [libraries]
            kobweb-core = { module = "com.varabyte.kobweb:kobweb-core", version.ref = "kobweb" }
            kobweb-silk = { module = "com.varabyte.kobweb:kobweb-silk", version.ref = "kobweb" }
            """
        )
        val userCatalog = configureUserCatalog(
            """
            [libraries]
            kobweb-core = "com.varabyte.kobweb:kobweb-core:0.24.0"
            """
        )

        userCatalog.copyEntriesIfAbsentInWriteCommand(
            from = templateCatalog,
            libraryAliases = listOf("kobweb-core", "kobweb-silk"),
            pluginAliases = emptyList(),
        )

        myFixture.checkResult(
            """
            [versions]
            kobweb = "0.24.0"
            [libraries]
            kobweb-core = "com.varabyte.kobweb:kobweb-core:0.24.0"
            kobweb-silk = { group = "com.varabyte.kobweb", name = "kobweb-silk", version.ref = "kobweb" }
            """.trimIndent()
        )
    }

    fun testSiblingVersionRefIsReused() {
        val templateCatalog = addTemplateCatalog(
            """
            [versions]
            kobweb = "0.25.1"

            [libraries]
            kobweb-core = { module = "com.varabyte.kobweb:kobweb-core", version.ref = "kobweb" }

            [plugins]
            kobweb-library = { id = "com.varabyte.kobweb.library", version.ref = "kobweb" }
            """
        )
        val userCatalog = configureUserCatalog(
            """
            [versions]
            kobwebVersion = "0.24.0"

            [libraries]
            kobweb-core = { module = "com.varabyte.kobweb:kobweb-core", version = { ref = "kobwebVersion" } }

            [plugins]
            """
        )

        userCatalog.copyEntriesIfAbsentInWriteCommand(
            from = templateCatalog,
            libraryAliases = listOf("kobweb-core"),
            pluginAliases = listOf("kobweb-library"),
        )

        myFixture.checkResult(
            """
            [versions]
            kobwebVersion = "0.24.0"

            [libraries]
            kobweb-core = { module = "com.varabyte.kobweb:kobweb-core", version = { ref = "kobwebVersion" } }

            [plugins]
            kobweb-library = { id = "com.varabyte.kobweb.library", version.ref = "kobwebVersion" }
            """.trimIndent()
        )
    }

    fun testTakenAliasGetsNumbered() {
        val templateCatalog = addTemplateCatalog(
            """
            [versions]
            kobweb = "0.25.1"

            [libraries]
            kobweb-core = { module = "com.varabyte.kobweb:kobweb-core", version.ref = "kobweb" }
            """
        )
        val userCatalog = configureUserCatalog(
            """
            [versions]
            kobweb = "0.25.1"

            [libraries]
            kobweb_core = { module = "org.example:not-kobweb-core", version = "1.0" }
            """
        )

        userCatalog.copyEntriesIfAbsentInWriteCommand(
            from = templateCatalog,
            libraryAliases = listOf("kobweb-core"),
            pluginAliases = emptyList(),
        )

        myFixture.checkResult(
            """
            [versions]
            kobweb = "0.25.1"

            [libraries]
            kobweb_core = { module = "org.example:not-kobweb-core", version = "1.0" }
            kobweb-core2 = { group = "com.varabyte.kobweb", name = "kobweb-core", version.ref = "kobweb" }
            """.trimIndent()
        )
    }

    fun testSiblingPluginVersionIsReusedForLibraries() {
        val templateCatalog = addTemplateCatalog(
            """
            [versions]
            kotlin = "2.4.10"

            [libraries]
            kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }

            [plugins]
            kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
            """
        )
        val userCatalog = configureUserCatalog(
            """
            [plugins]
            kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version = "2.4.0" }
            """
        )

        userCatalog.copyEntriesIfAbsentInWriteCommand(
            from = templateCatalog,
            libraryAliases = listOf("kotlin-stdlib"),
            pluginAliases = emptyList(),
        )

        myFixture.checkResult(
            """
            [versions]
            kotlin = "2.4.0"
            [libraries]
            kotlin-stdlib = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version.ref = "kotlin" }
            [plugins]
            kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version = "2.4.0" }
            """.trimIndent()
        )
    }
}
