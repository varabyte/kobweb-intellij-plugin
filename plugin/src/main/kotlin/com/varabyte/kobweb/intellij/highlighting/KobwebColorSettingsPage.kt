package com.varabyte.kobweb.intellij.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.idea.KotlinLanguage
import javax.swing.Icon

/**
 * Lets users change the colors Kobweb highlights code with, under Settings | Editor | Color Scheme | Kobweb.
 */
class KobwebColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = UxGlobals.textBundle.message("kobweb.name")

    override fun getIcon(): Icon = UxGlobals.gutterIcon

    // The demo text is Kotlin, so highlight the rest of it like Kotlin
    override fun getHighlighter(): SyntaxHighlighter =
        SyntaxHighlighterFactory.getSyntaxHighlighter(
            /* language = */ KotlinLanguage.INSTANCE,
            /* project = */ null,
            /* file = */ null
        ) ?: PlainSyntaxHighlighter()

    override fun getDemoText() = """
        // pages/users/Posts.kt
        @Page("{user}/posts/{post}")
        @Composable
        fun UserPostPage() {
        }

        @Composable
        fun Nav() {
            Link("/users/<dynamicSegment>bitspittle</dynamicSegment>/posts/<dynamicSegment>123</dynamicSegment>")
        }
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> =
        mapOf("dynamicSegment" to KobwebHighlightingColors.DYNAMIC_ROUTE_SEGMENT_VALUE)

    override fun getAttributeDescriptors() = arrayOf(
        AttributesDescriptor(
            UxGlobals.textBundle.lazyMessage("highlighting.dynamic.route.segment.value.display.name"),
            KobwebHighlightingColors.DYNAMIC_ROUTE_SEGMENT_VALUE,
        ),
    )

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
}
