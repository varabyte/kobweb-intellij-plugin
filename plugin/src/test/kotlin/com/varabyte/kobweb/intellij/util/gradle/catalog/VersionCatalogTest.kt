@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.util.gradle.catalog

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.varabyte.kobweb.intellij.util.gradle.catalog.VersionCatalog.Entry
import com.varabyte.kobweb.intellij.util.gradle.catalog.VersionCatalog.Version
import com.varabyte.truthish.assertThat

class VersionCatalogTest : BasePlatformTestCase() {
    private fun PsiFile.toCatalog() =
        VersionCatalog(ProjectBuildModel.get(project).versionCatalogsModel.getVersionCatalogModel(virtualFile, "libs"))

    fun testEntriesAreReadInAllForms() {
        val catalog = myFixture.addFileToProject(
            "libs.versions.toml",
            """
            [versions]
            kobweb = "0.25.1"
            kotlin = "2.4.10"

            [libraries]
            kobweb-core = { module = "com.varabyte.kobweb:kobweb-core", version.ref = "kobweb" }
            kobweb-silk = { group = "com.varabyte.kobweb", name = "kobweb-silk", version = { ref = "kobweb" } }
            kobweb-worker = "com.varabyte.kobweb:kobweb-worker:0.25.1"
            kotlinx-coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version = "1.10.2" }
            ktor-client = { module = "io.ktor:ktor-client-core" }

            [plugins]
            kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
            kobweb-application = "com.varabyte.kobweb.application:0.25.1"
            """.trimIndent()
        ).toCatalog()

        assertThat(catalog.versions).containsExactly("kobweb" to "0.25.1", "kotlin" to "2.4.10")
        assertThat(catalog.libraries).containsExactly(
            "kobweb-core" to Entry("com.varabyte.kobweb:kobweb-core", Version.Ref("kobweb")),
            "kobweb-silk" to Entry("com.varabyte.kobweb:kobweb-silk", Version.Ref("kobweb")),
            "kobweb-worker" to Entry("com.varabyte.kobweb:kobweb-worker", Version.Literal("0.25.1")),
            "kotlinx-coroutines" to Entry("org.jetbrains.kotlinx:kotlinx-coroutines-core", Version.Literal("1.10.2")),
            "ktor-client" to Entry("io.ktor:ktor-client-core", null),
        )
        assertThat(catalog.plugins).containsExactly(
            "kotlin-multiplatform" to Entry("org.jetbrains.kotlin.multiplatform", Version.Ref("kotlin")),
            "kobweb-application" to Entry("com.varabyte.kobweb.application", Version.Literal("0.25.1")),
        )
    }

    fun testAliasesAreFoundById() {
        val catalog = myFixture.addFileToProject(
            "libs.versions.toml",
            """
            [libraries]
            core = "com.varabyte.kobweb:kobweb-core:0.24.0"
            silk = { group = "com.varabyte.kobweb", name = "kobweb-silk", version = "0.24.0" }

            [plugins]
            kobweb-lib = "com.varabyte.kobweb.library:0.24.0"
            """.trimIndent()
        ).toCatalog()

        assertThat(catalog.findLibraryAlias("com.varabyte.kobweb:kobweb-core")).isEqualTo("core")
        assertThat(catalog.findLibraryAlias("com.varabyte.kobweb:kobweb-silk")).isEqualTo("silk")
        assertThat(catalog.findLibraryAlias("com.varabyte.kobweb:kobweb-worker")).isNull()
        assertThat(catalog.findPluginAlias("com.varabyte.kobweb.library")).isEqualTo("kobweb-lib")
        assertThat(catalog.findPluginAlias("com.varabyte.kobweb.application")).isNull()
    }

    fun testAccessorsFollowGradleNaming() {
        val catalog = myFixture.addFileToProject("libs.versions.toml", "").toCatalog()

        assertThat(catalog.libraryAccessor("kobweb-core")).isEqualTo("libs.kobweb.core")
        assertThat(catalog.libraryAccessor("kotlinx_coroutines.core")).isEqualTo("libs.kotlinx.coroutines.core")
        assertThat(catalog.pluginAccessor("kobweb-library")).isEqualTo("libs.plugins.kobweb.library")
    }
}
