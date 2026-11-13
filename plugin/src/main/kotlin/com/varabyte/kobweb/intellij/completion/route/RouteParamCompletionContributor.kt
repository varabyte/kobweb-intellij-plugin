package com.varabyte.kobweb.intellij.completion.route

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.DynamicRouteSegment
import com.varabyte.kobweb.intellij.util.kobweb.route.displayValue
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteParamsRoute
import com.varabyte.kobweb.intellij.util.kobweb.route.isRouteParamKey
import com.varabyte.kobweb.intellij.util.kobweb.route.mayBeRouteParamKey
import com.varabyte.kobweb.intellij.util.kobweb.route.segments
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Suggests the names of a route's dynamic segments as keys into its route params, e.g. "post" while typing
 * `ctx.route.params["p"]` in a page registered at "/blog/{post}", or `ctx.req.params["p"]` in an API endpoint
 * registered at "/api/blog/{post}".
 */
class RouteParamCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(KtStringTemplateExpression::class.java),
            RouteParamCompletionProvider,
        )
    }
}

/**
 * Returns the text of this string up to [offset] (a position in its file) if it could be the start of a route param
 * key that we'd complete, e.g. "p" for `ctx.route.params["p|"]`.
 *
 * Returns null if it can't be, e.g. if the string isn't used as a key into route params (see [mayBeRouteParamKey]) or
 * it has template expressions or escapes before [offset].
 */
internal fun KtStringTemplateExpression.findRouteParamPrefix(offset: Int): String? {
    if (!mayBeRouteParamKey()) return null

    val valueRange = ElementManipulators.getValueTextRange(this).shiftRight(textRange.startOffset)
    if (offset !in valueRange.startOffset..valueRange.endOffset) return null
    val entriesBefore = entries.filter { entry -> entry.textRange.startOffset < offset }
    if (entriesBefore.any { entry -> entry !is KtLiteralStringTemplateEntry }) return null

    return TextRange(valueRange.startOffset, offset).substring(containingFile.text)
}

private object RouteParamCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val stringExpr = parameters.position.parentOfType<KtStringTemplateExpression>() ?: return
        val prefix = stringExpr.findRouteParamPrefix(parameters.offset) ?: return

        // During completion, `stringExpr` lives in a temporary copy of the file (with a dummy identifier inserted at
        // the caret). Map it back to the real file, so we can analyze it and look up which Kobweb project it's in.
        val originalStringExpr = CompletionUtil.getOriginalOrSelf(stringExpr)
        if (!originalStringExpr.isInWritableKobwebProject()) return
        val route = originalStringExpr.findRouteParamsRoute() ?: return
        if (!analyze(originalStringExpr) { originalStringExpr.isRouteParamKey() }) return

        val names = route.segments.mapNotNull { segment -> DynamicRouteSegment.tryCreate(segment)?.name }
        val nameResult = result.withPrefixMatcher(prefix)
        names.forEachIndexed { i, name ->
            val element = LookupElementBuilder.create(name).withTypeText(route.displayValue)
            // Keep the names in route order, as otherwise IntelliJ sorts them alphabetically
            nameResult.addElement(PrioritizedLookupElement.withPriority(element, (names.size - i).toDouble()))
        }

        // Otherwise, IntelliJ suggests the project's file paths too, which aren't keys
        result.stopHere()
    }
}
