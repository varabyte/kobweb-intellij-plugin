package com.varabyte.kobweb.intellij.completion.route

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.varabyte.kobweb.intellij.util.kobweb.route.mayHaveRoutePathTarget
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Returns the text of this string up to [offset] (a position in its file) if it could be the start of a route that
 * we'd complete, e.g. "/bl" for `Link("/bl|")`, or "" for `Link("|")`.
 *
 * Returns null if it can't be, e.g. if the string isn't used in a way that could hold a route (see
 * [mayHaveRoutePathTarget]), the text is relative ("blog"), it already has a query or fragment, or it has template
 * expressions or escapes before [offset].
 *
 * Note this doesn't check whether the string is used with something that holds a route, which requires Kotlin
 * analysis.
 */
internal fun KtStringTemplateExpression.findRoutePrefix(offset: Int): String? {
    if (!mayHaveRoutePathTarget()) return null

    val valueRange = ElementManipulators.getValueTextRange(this).shiftRight(textRange.startOffset)
    if (offset !in valueRange.startOffset..valueRange.endOffset) return null
    val entriesBefore = entries.filter { entry -> entry.textRange.startOffset < offset }
    if (entriesBefore.any { entry -> entry !is KtLiteralStringTemplateEntry }) return null

    val prefix = TextRange(valueRange.startOffset, offset).substring(containingFile.text)
    if (prefix.isNotEmpty() && !prefix.startsWith('/')) return null
    if (prefix.any { c -> c == '?' || c == '#' }) return null
    return prefix
}
