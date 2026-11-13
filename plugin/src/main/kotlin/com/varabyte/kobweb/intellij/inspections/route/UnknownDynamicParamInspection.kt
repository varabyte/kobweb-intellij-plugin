package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.route.ChooseDynamicParamQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.DynamicRouteSegment
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteParamKey
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteParamsScope
import com.varabyte.kobweb.intellij.util.kobweb.route.displayValue
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteParamsRoute
import com.varabyte.kobweb.intellij.util.kobweb.route.segments
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports keys into `dynamicParams` that aren't the name of one of the route's dynamic segments, e.g.
 * `ctx.route.dynamicParams["pots"]` in a page registered at "/blog/{post}", which can only ever be null.
 *
 * Keys into `params` aren't checked, as it holds query parameters too, so any key might be in it.
 */
class UnknownDynamicParamInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)
                val paramKey = RouteParamKey.from(expression) ?: return
                if (paramKey.scopeFromName != RouteParamsScope.DYNAMIC_ONLY) return
                val key = paramKey.value ?: return

                val route = expression.findRouteParamsRoute() ?: return
                val names = route.segments.mapNotNull { segment -> DynamicRouteSegment.tryCreate(segment)?.name }
                if (key in names) return

                // Checked last, as it's the most expensive check
                if (analyze(expression) { paramKey.findScope() } != RouteParamsScope.DYNAMIC_ONLY) return

                holder.registerProblem(
                    expression,
                    ElementManipulators.getValueTextRange(expression),
                    UxGlobals.textBundle.message(
                        "inspection.unknown.dynamic.param.display.text",
                        route.displayValue,
                        key,
                    ),
                    // Without any dynamic segments, there's nothing to choose from
                    *listOfNotNull(names.takeIf { it.isNotEmpty() }?.let(::ChooseDynamicParamQuickFix)).toTypedArray(),
                )
            }
        }
    }
}
