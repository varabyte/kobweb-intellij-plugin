@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.schema.conf

import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat

// IntelliJ only fetches remote-only schemas from 2026.3 on, which is newer than the IDE that tests run against, so
// these tests check the provider directly instead of asking `JsonSchemaService` which schema a file gets
class KobwebConfSchemaProviderFactoryTest : KobwebApplicationTestCase() {
    private val provider: JsonSchemaFileProvider
        get() = JsonSchemaProviderFactory.EP_NAME.findExtensionOrFail(KobwebConfSchemaProviderFactory::class.java)
            .getProviders(project)
            .single()

    private fun isAvailableFor(path: String): Boolean {
        val file = myFixture.addFileToProject(path, "").virtualFile
        return provider.isAvailable(file)
    }

    fun testProviderPointsAtKobwebSchema() {
        assertThat(provider.remoteSource)
            .isEqualTo("https://raw.githubusercontent.com/varabyte/kobweb/main/schemas/kobweb-conf.schema.json")
    }

    fun testKobwebConfGetsSchema() {
        assertThat(isAvailableFor("site/.kobweb/conf.yaml")).isTrue()
        assertThat(isAvailableFor(".kobweb/conf.yaml")).isTrue()
    }

    fun testOtherYamlFilesDontGetSchema() {
        assertThat(isAvailableFor("site/conf.yaml")).isFalse()
        assertThat(isAvailableFor("site/.kobweb/other.yaml")).isFalse()
    }
}
