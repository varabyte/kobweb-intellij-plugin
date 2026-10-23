package com.varabyte.kobweb.intellij.util.kobweb

import com.varabyte.truthish.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class KobwebMetadataTest {
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
}
