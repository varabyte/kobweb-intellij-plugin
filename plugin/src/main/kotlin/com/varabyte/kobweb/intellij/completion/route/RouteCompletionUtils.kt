package com.varabyte.kobweb.intellij.completion.route

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.varabyte.kobweb.intellij.util.kobweb.route.mayHavePathTarget
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Returns the text of this string up to [offset] (a position in its file) if it could be the start of a path that we'd
 * complete, e.g. "/bl" for `Link("/bl|")`, "he" for `window.api.get("he|")`, or "" for `Link("|")`.
 *
 * Returns null if it can't be, e.g. if the string isn't used in a way that could hold a path (see
 * [mayHavePathTarget]), it already has a query or fragment, or it has template expressions or escapes before [offset].
 *
 * Note this doesn't check whether the string is used with something that holds a path, which requires Kotlin
 * analysis.
 */
internal fun KtStringTemplateExpression.findPathPrefix(offset: Int): String? {
    if (!mayHavePathTarget()) return null

    val valueRange = ElementManipulators.getValueTextRange(this).shiftRight(textRange.startOffset)
    if (offset !in valueRange.startOffset..valueRange.endOffset) return null
    val entriesBefore = entries.filter { entry -> entry.textRange.startOffset < offset }
    if (entriesBefore.any { entry -> entry !is KtLiteralStringTemplateEntry }) return null

    val prefix = TextRange(valueRange.startOffset, offset).substring(containingFile.text)
    if (prefix.any { c -> c == '?' || c == '#' }) return null
    return prefix
}

/**
 * Like [findPathPrefix], but only for text that could be the start of a route, which is never relative ("blog").
 */
internal fun KtStringTemplateExpression.findRoutePrefix(offset: Int): String? {
    return findPathPrefix(offset)?.takeIf { prefix -> prefix.isEmpty() || prefix.startsWith('/') }
}
