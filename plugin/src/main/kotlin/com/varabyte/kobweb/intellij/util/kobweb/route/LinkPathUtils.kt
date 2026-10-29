package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * The parts of this string, in order, for parsing as a [LinkPath] (see [LinkPath.Part]), positioned relative to this
 * string.
 */
private val KtStringTemplateExpression.linkPathParts: List<LinkPath.Part>
    get() = entries.map { entry ->
        val text = when (entry) {
            is KtLiteralStringTemplateEntry -> entry.text
            is KtEscapeStringTemplateEntry -> entry.unescapedValue
            else -> null
        }
        LinkPath.Part(text, entry.startOffsetInParent until entry.startOffsetInParent + entry.textLength)
    }

/**
 * Returns the path in this string (see [LinkPath.parse]), e.g. "/users/bob" for `Link("/users/bob")`, or "/users/bob"
 * for `window.api.get("users/bob")` (with [allowRelativePath]).
 *
 * @param allowRelativePath See [LinkPath.parse].
 */
fun KtStringTemplateExpression.parseLinkPath(allowRelativePath: Boolean = false): LinkPath? =
    LinkPath.parse(linkPathParts, allowRelativePath)

/**
 * Returns the range of the path inside this string, i.e. without any query parameters or fragment.
 */
fun KtStringTemplateExpression.linkPathRange(): TextRange {
    val valueRange = ElementManipulators.getValueTextRange(this)
    val queryOrFragmentStart = entries
        .filterIsInstance<KtLiteralStringTemplateEntry>()
        .firstNotNullOfOrNull { entry ->
            entry.text.indexOfAny(charArrayOf('?', '#')).takeIf { it >= 0 }?.let { entry.startOffsetInParent + it }
        }
    return if (queryOrFragmentStart != null) TextRange(valueRange.startOffset, queryOrFragmentStart) else valueRange
}
