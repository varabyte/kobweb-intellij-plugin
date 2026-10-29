package com.varabyte.kobweb.intellij.completion.api

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.varabyte.kobweb.intellij.completion.route.DynamicSegmentsInsertHandler
import com.varabyte.kobweb.intellij.completion.route.findPathPrefix
import com.varabyte.kobweb.intellij.util.kobweb.api.ApiDefinition
import com.varabyte.kobweb.intellij.util.kobweb.api.ApiPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiDefinitions
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.api.get
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.DynamicRouteSegment
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Suggests the paths of API endpoints inside strings used with something that holds an API path (see
 * [findApiPathTarget]), e.g. "hello" while typing `window.api.get("he")`, or "chat" while typing `ApiStream("ch")`.
 *
 * API paths are relative to "/api/" and don't need a leading slash, so suggestions don't have one either, unless one
 * was already typed (e.g. "/hello" for `window.api.get("/he")`), as Kobweb accepts both.
 *
 * Paths with dynamic segments (e.g. "users/{user}") are inserted as a template, so that the value of each dynamic
 * segment can be typed in turn.
 */
class ApiPathCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(KtStringTemplateExpression::class.java),
            ApiPathCompletionProvider,
        )
    }
}

private object ApiPathCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val stringExpr = parameters.position.parentOfType<KtStringTemplateExpression>() ?: return
        val prefix = stringExpr.findPathPrefix(parameters.offset) ?: return

        // During completion, `stringExpr` lives in a temporary copy of the file (with a dummy identifier inserted at
        // the caret). Map it back to the real file, so we can analyze it and look up which Kobweb project it belongs to.
        val originalStringExpr = CompletionUtil.getOriginalOrSelf(stringExpr)
        if (!originalStringExpr.isInWritableKobwebProject()) return
        val target = analyze(originalStringExpr) { originalStringExpr.findApiPathTarget() } ?: return

        val withLeadingSlash = prefix.startsWith('/')
        // Match against everything typed so far in the string, not just the text after the last slash. Also, keep the
        // paths in the order we add them, as otherwise IntelliJ ranks them by where they're declared, e.g. putting the
        // ones from libraries last.
        val pathResult = result.withPrefixMatcher(prefix).withRelevanceSorter(CompletionSorter.emptySorter())
        originalStringExpr.findApiDefinitions()[target.kind]
            .sortedBy { api -> api.route }
            .forEach { api -> pathResult.addElement(api.toLookupElement(target.kind, withLeadingSlash)) }

        // Otherwise, IntelliJ suggests the project's file paths too, which aren't API paths
        result.stopHere()
    }

    private fun ApiDefinition.toLookupElement(kind: ApiPathTarget.Kind, withLeadingSlash: Boolean): LookupElement {
        val path = if (withLeadingSlash) route else route.removePrefix("/")
        // Kobweb doesn't currently allow dynamic segments in stream routes (see `findApiStreamRoute`)
        val hasDynamicSegments = kind != ApiPathTarget.Kind.STREAM
                && path.split('/').any { segment -> DynamicRouteSegment.tryCreate(segment) != null }
        return LookupElementBuilder.create(declaration ?: fqn, path)
            .withTypeText(fqn.substringAfterLast('.'))
            .withIcon(
                when (kind) {
                    ApiPathTarget.Kind.API -> KotlinIcons.FUNCTION
                    ApiPathTarget.Kind.STREAM -> KotlinIcons.FIELD_VAL
                }
            )
            .withInsertHandler(if (hasDynamicSegments) DynamicSegmentsInsertHandler else null)
    }
}
