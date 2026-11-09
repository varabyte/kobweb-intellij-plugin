package com.varabyte.kobweb.intellij.util.kobweb

import com.varabyte.kobweb.intellij.model.IconsBlock
import com.varabyte.truthish.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class KobwebMetadataTest {
    @Test
    fun readModuleMetadata() {
        val metadata = Json.readKobwebModuleMetadata(
            """
            { "kobwebVersion": "0.25.2", "group": "com.example", "publicPath": "static" }
            """.trimIndent()
        )!!

        assertThat(metadata.kobwebVersion).isEqualTo("0.25.2")
        assertThat(metadata.group).isEqualTo("com.example")
        assertThat(metadata.publicPath).isEqualTo("static")
    }

    @Test
    fun readModuleMetadataWithUnknownKeys() {
        // Newer versions of Kobweb may record more than we know about
        val metadata = Json.readKobwebModuleMetadata(
            """
            { "kobwebVersion": "0.30.0", "group": "com.example", "publicPath": "public", "somethingNew": { "a": 1 } }
            """.trimIndent()
        )!!

        assertThat(metadata.kobwebVersion).isEqualTo("0.30.0")
        assertThat(metadata.publicPath).isEqualTo("public")
    }

    @Test
    fun readModuleMetadataWithoutGroup() {
        // Kobweb only started recording the group and publicPath in 0.25.2
        val metadata = Json.readKobwebModuleMetadata(
            """
            { "kobwebVersion": "0.25.1" }
            """.trimIndent()
        )!!

        assertThat(metadata.kobwebVersion).isEqualTo("0.25.1")
        assertThat(metadata.group).isNull()
        assertThat(metadata.publicPath).isNull()
    }

    @Test
    fun readModuleMetadataWithoutVersion() {
        // Kobweb always records its version, so the file is malformed without it
        assertThat(Json.readKobwebModuleMetadata("""{ "group": "com.example" }""")).isNull()
    }

    @Test
    fun readMalformedModuleMetadata() {
        assertThat(Json.readKobwebModuleMetadata("not json")).isNull()
        assertThat(Json.readKobwebModuleMetadata("[]")).isNull()
    }

    @Test
    fun readFrontendMetadata() {
        // Trimmed down from a real library's frontend.json.
        val metadata = Json.readKobwebFrontendMetadata(
            """
            {
                "layouts": [
                    { "fqn": "com.example.layouts.PageLayout", "parentLayoutFqn": null }
                ],
                "pages": [
                    { "fqn": "com.example.pages.HomePage", "route": "/", "acceptsContext": false },
                    { "fqn": "com.example.pages.blog.PostPage", "route": "/blog/{post}", "acceptsContext": true }
                ],
                "cssStyles": []
            }
            """.trimIndent()
        )!!

        assertThat(metadata.layouts.map { it.fqn }).containsExactly("com.example.layouts.PageLayout")
        assertThat(metadata.pages.map { it.fqn to it.route }).containsExactly(
            "com.example.pages.HomePage" to "/",
            "com.example.pages.blog.PostPage" to "/blog/{post}",
        )
    }

    @Test
    fun readFrontendMetadataSkipsMalformedEntries() {
        val metadata = Json.readKobwebFrontendMetadata(
            """
            {
                "layouts": [ { "notFqn": "?" } ],
                "pages": [
                    { "fqn": "com.example.pages.NoRoutePage" },
                    { "fqn": "com.example.pages.AboutPage", "route": "/about" }
                ]
            }
            """.trimIndent()
        )!!

        assertThat(metadata.layouts).isEmpty()
        assertThat(metadata.pages.map { it.fqn }).containsExactly("com.example.pages.AboutPage")
    }

    @Test
    fun readFrontendMetadataWithMissingLists() {
        val metadata = Json.readKobwebFrontendMetadata("{}")!!
        assertThat(metadata.layouts).isEmpty()
        assertThat(metadata.pages).isEmpty()
    }

    @Test
    fun readMalformedFrontendMetadata() {
        assertThat(Json.readKobwebFrontendMetadata("not json")).isNull()
        assertThat(Json.readKobwebFrontendMetadata("[]")).isNull()
    }

    @Test
    fun readBackendMetadata() {
        val metadata = Json.readKobwebBackendMetadata(
            """
            {
                "initMethods": [ { "fqn": "com.example.api.initApi" } ],
                "apiMethods": [
                    { "fqn": "com.example.api.hello", "route": "/hello" },
                    { "fqn": "com.example.api.users.user", "route": "/users/{user}" }
                ],
                "apiStreamProperties": [
                    { "fqn": "com.example.api.chat", "route": "/chat" }
                ]
            }
            """.trimIndent()
        )!!

        assertThat(metadata.apiMethods.map { it.fqn to it.route }).containsExactly(
            "com.example.api.hello" to "/hello",
            "com.example.api.users.user" to "/users/{user}",
        )
        assertThat(metadata.apiStreams.map { it.fqn to it.route }).containsExactly("com.example.api.chat" to "/chat")
    }

    @Test
    fun readBackendMetadataWithLegacyStreamListName() {
        val metadata = Json.readKobwebBackendMetadata(
            """
            { "apiStreamMethods": [ { "fqn": "com.example.api.chat", "route": "/chat" } ] }
            """.trimIndent()
        )!!

        assertThat(metadata.apiStreams.map { it.fqn }).containsExactly("com.example.api.chat")
    }

    @Test
    fun readBackendMetadataSkipsMalformedEntries() {
        val metadata = Json.readKobwebBackendMetadata(
            """
            {
                "apiMethods": [
                    { "fqn": "com.example.api.noRoute" },
                    { "fqn": "com.example.api.hello", "route": "/hello" }
                ]
            }
            """.trimIndent()
        )!!

        assertThat(metadata.apiMethods.map { it.fqn }).containsExactly("com.example.api.hello")
        assertThat(metadata.apiStreams).isEmpty()
    }

    @Test
    fun readMalformedBackendMetadata() {
        assertThat(Json.readKobwebBackendMetadata("not json")).isNull()
        assertThat(Json.readKobwebBackendMetadata("[]")).isNull()
    }

    @Test
    fun readIconsMetadata() {
        val metadata = Json.readKobwebIconsMetadata(
            """
            {
                "iconSets": {
                    "fa": { "version": "6.5.2", "packageName": "com.example.components.icons.fa" },
                    "lucide": { "version": "1.70.0", "packageName": "com.example.components.icons.lucide" }
                }
            }
            """.trimIndent()
        )!!

        assertThat(metadata.iconSets.mapValues { it.value.packageName }).containsExactly(
            "fa" to "com.example.components.icons.fa",
            "lucide" to "com.example.components.icons.lucide",
        )
    }

    @Test
    fun readIconsMetadataSkipsMalformedIconSets() {
        val metadata = Json.readKobwebIconsMetadata(
            """
            {
                "iconSets": {
                    "fa": { "version": "6.5.2", "packageName": "com.example.components.icons.fa" },
                    "lucide": { "version": "1.70.0" }
                }
            }
            """.trimIndent()
        )!!

        assertThat(metadata.iconSets.keys).containsExactly("fa")
    }

    @Test
    fun readMalformedIconsMetadata() {
        assertThat(Json.readKobwebIconsMetadata("not json")).isNull()
        assertThat(Json.readKobwebIconsMetadata("[]")).isNull()
    }

    @Test
    fun iconsMetadataToIconsBlock() {
        val metadata = KobwebIconsMetadata(
            mapOf(
                "fa" to KobwebIconsMetadata.IconSet("com.example.components.icons.fa"),
                "lucide" to KobwebIconsMetadata.IconSet("com.example.components.icons.lucide"),
            )
        )

        assertThat(metadata.toIconsBlock()).isEqualTo(
            IconsBlock(rootPackage = "com.example.components.icons", iconSets = listOf("fa", "lucide"))
        )
    }

    @Test
    fun iconsMetadataWithUnexpectedPackagesHasNoIconsBlock() {
        // Kobweb always generates each icon set into a subpackage named after it, under one root package
        val notNamedAfterSet = KobwebIconsMetadata(
            mapOf("fa" to KobwebIconsMetadata.IconSet("com.example.components.icons.fontawesome"))
        )
        val differentRoots = KobwebIconsMetadata(
            mapOf(
                "fa" to KobwebIconsMetadata.IconSet("com.example.components.icons.fa"),
                "lucide" to KobwebIconsMetadata.IconSet("com.example.widgets.lucide"),
            )
        )

        assertThat(notNamedAfterSet.toIconsBlock()).isNull()
        assertThat(differentRoots.toIconsBlock()).isNull()
        assertThat(KobwebIconsMetadata(emptyMap()).toIconsBlock()).isNull()
    }
}
