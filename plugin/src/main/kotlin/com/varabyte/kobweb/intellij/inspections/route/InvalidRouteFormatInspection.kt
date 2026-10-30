package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.RoutePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoutePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.isFullUrl
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import com.varabyte.kobweb.intellij.util.kobweb.route.mayHavePathTarget
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports route strings that aren't in the format expected by the parameter or property they're used with, according
 * to its `@RoutePath(type = ...)` annotation, e.g. a full URL passed to `Router.tryRoutingTo`, which only takes a path
 * (plus optional query parameters and fragment).
 *
 * This applies to any string used with an annotated parameter or property (see [findRoutePathTarget]), e.g.
 * `ctx.route.path == "/blog?sort=new"`, which can never be true, as `path` only holds a path.
 */
class InvalidRouteFormatInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)
                if (!expression.mayHavePathTarget()) return

                val isFullUrl = expression.isFullUrl
                val valueRange = ElementManipulators.getValueTextRange(expression)
                // Everything after the path, if anything, is the query parameters and fragment
                val queryAndFragmentRange = TextRange(expression.linkPathRange().endOffset, valueRange.endOffset)
                    .takeIf { range -> !range.isEmpty }
                // Checked before analysis, as most strings are fine whatever the target holds
                if (!isFullUrl && queryAndFragmentRange == null) return

                val target = analyze(expression) { expression.findRoutePathTarget() } ?: return
                fun reportFullUrl() = holder.registerProblem(
                    expression,
                    valueRange,
                    UxGlobals.textBundle.message("inspection.invalid.route.format.full.url.display.text"),
                )
                fun reportQueryAndFragment(range: TextRange) = holder.registerProblem(
                    expression,
                    range,
                    UxGlobals.textBundle.message("inspection.invalid.route.format.query.display.text"),
                )

                when (target.type) {
                    RoutePathTarget.Type.URL -> {}
                    RoutePathTarget.Type.PATH_QUERY_AND_FRAGMENT -> if (isFullUrl) reportFullUrl()
                    RoutePathTarget.Type.PATH_ONLY -> when {
                        isFullUrl -> reportFullUrl()
                        queryAndFragmentRange != null -> reportQueryAndFragment(queryAndFragmentRange)
                    }
                }
            }
        }
    }
}
