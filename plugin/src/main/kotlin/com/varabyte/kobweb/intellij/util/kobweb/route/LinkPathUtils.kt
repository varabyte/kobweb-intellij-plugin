package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * The parts of this string, in order, for parsing as a [LinkPath]: its text (with escape sequences decoded), or null for
 * template expressions (e.g. "$id"), whose values can't be known until runtime.
 */
private val KtStringTemplateExpression.linkPathParts: List<String?>
    get() = entries.map { entry ->
        when (entry) {
            is KtLiteralStringTemplateEntry -> entry.text
            is KtEscapeStringTemplateEntry -> entry.unescapedValue
            else -> null
        }
    }

/**
 * The path in this string, if it's one that could lead to a page (see [LinkPath.parse]).
 */
val KtStringTemplateExpression.linkPath: LinkPath? get() = LinkPath.parse(linkPathParts)

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
