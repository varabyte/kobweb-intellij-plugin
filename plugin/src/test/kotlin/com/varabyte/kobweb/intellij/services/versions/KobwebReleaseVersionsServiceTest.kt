@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.services.versions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.varabyte.truthish.assertThat

class KobwebReleaseVersionsServiceTest : BasePlatformTestCase() {
    fun testReleaseTagIsFoundInReleaseUrl() {
        val tag = KobwebReleaseVersionsService.findReleaseTag("https://github.com/varabyte/kobweb/releases/tag/v0.25.1")

        assertThat(tag).isEqualTo("v0.25.1")
    }

    fun testUrlsWithoutReleaseTagAreRejected() {
        val service = KobwebReleaseVersionsService

        assertThat(service.findReleaseTag("https://github.com/varabyte/kobweb/releases")).isNull()
        assertThat(service.findReleaseTag("https://github.com/varabyte/kobweb/releases/tag/0.25.1")).isNull()
        assertThat(service.findReleaseTag("https://github.com/varabyte/kobweb/releases/tag/v0.26.0-rc1")).isNull()
    }

    fun testVersionsAreReadFromCatalog() {
        val versions = KobwebReleaseVersionsService.readVersions(
            project,
            """
            [versions]
            kobweb = "0.25.1"
            #------------------------
            compose-runtime = "1.12.0"
            kotlin = "2.4.10"
            rich = { strictly = "1.0" }

            [libraries]
            kobweb-core = { module = "com.varabyte.kobweb:kobweb-core", version.ref = "kobweb" }
            """.trimIndent(),
        )

        assertThat(versions).isEqualTo(
            mapOf(
                "kobweb" to "0.25.1",
                "compose-runtime" to "1.12.0",
                "kotlin" to "2.4.10",
            )
        )
    }

    fun testCatalogWithoutVersionsTableHasNoVersions() {
        val versions = KobwebReleaseVersionsService.readVersions(
            project,
            """
            [libraries]
            kobweb-core = "com.varabyte.kobweb:kobweb-core:0.25.1"
            """.trimIndent(),
        )

        assertThat(versions).isEmpty()
    }
}
