package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.route.ReplaceWithDynamicParamGetValueQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.DynamicRouteSegment
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteParamsScope
import com.varabyte.kobweb.intellij.util.kobweb.route.canSwitchToDynamicParams
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteParamsRoute
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteParamsScope
import com.varabyte.kobweb.intellij.util.kobweb.route.findValueLookup
import com.varabyte.kobweb.intellij.util.kobweb.route.mayBeRouteParamKey
import com.varabyte.kobweb.intellij.util.kobweb.route.segments
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports lookups of a route's dynamic segment that could be `dynamicParams.getValue`, e.g. `dynamicParams["post"]`,
 * `dynamicParams.getOrElse("post") { ... }`, or `params["post"]` in a page registered at "/blog/{post}".
 *
 * Kobweb always fills in every dynamic segment, so no null check or default is needed. Reading the key from
 * `dynamicParams` rather than `params` also gets it checked against the route (see [UnknownDynamicParamInspection]), so
 * renaming the segment later can't make `getValue` fail at runtime without warning.
 */
class DynamicParamLookupInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)
                if (!expression.mayBeRouteParamKey()) return
                val lookup = expression.findValueLookup() ?: return
                val call = (lookup as? KtQualifiedExpression)?.selectorExpression as? KtCallExpression
                // Already what we'd suggest
                if (call?.calleeExpression?.text == "getValue"
                    && expression.mayBeRouteParamKey(RouteParamsScope.DYNAMIC_ONLY)
                ) return
                if (expression.entries.any { entry -> entry !is KtLiteralStringTemplateEntry }) return
                val key = expression.entries.joinToString("") { entry -> entry.text }

                val route = expression.findRouteParamsRoute() ?: return
                val names = route.segments.mapNotNull { segment -> DynamicRouteSegment.tryCreate(segment)?.name }
                if (key !in names) return

                // Checked last, as it's the most expensive check
                val scope = analyze(expression) {
                    expression.findRouteParamsScope()?.takeIf { scope ->
                        scope == RouteParamsScope.DYNAMIC_ONLY || expression.canSwitchToDynamicParams()
                    }
                } ?: return

                // Just the lookup part, e.g. `["post"]` or `.get("post")`
                val lookupStart = when (lookup) {
                    is KtArrayAccessExpression -> lookup.indicesNode.textRange.startOffset
                    is KtQualifiedExpression -> lookup.operationTokenNode.startOffset
                    else -> return
                }
                val messageKey = when (scope) {
                    RouteParamsScope.DYNAMIC_ONLY -> "inspection.dynamic.param.lookup.dynamic.params.display.text"
                    RouteParamsScope.QUERY_AND_DYNAMIC -> "inspection.dynamic.param.lookup.params.display.text"
                }
                holder.registerProblem(
                    lookup,
                    TextRange(lookupStart - lookup.textRange.startOffset, lookup.textLength),
                    UxGlobals.textBundle.message(messageKey, key),
                    ReplaceWithDynamicParamGetValueQuickFix(),
                )
            }
        }
    }
}
