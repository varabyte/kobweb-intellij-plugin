package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.RoutePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoutePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import com.varabyte.kobweb.intellij.util.kobweb.route.mayHaveRoutePathTarget
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/** Matches the scheme at the start of a URL, e.g. "https:" or "mailto:". */
private val SCHEME_REGEX = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

/**
 * True if this string starts with a scheme or an authority (e.g. "https://example.com", "//example.com", or
 * "mailto:me@example.com"), i.e. it's a full URL rather than a path within the site.
 */
private val KtStringTemplateExpression.isFullUrl: Boolean
    get() {
        val text = (entries.firstOrNull() as? KtLiteralStringTemplateEntry)?.text ?: return false
        return text.startsWith("//") || SCHEME_REGEX.containsMatchIn(text)
    }

/**
 * Reports route strings that aren't in the format expected by the parameter or property they're used with, according
 * to its `@RoutePath(type = ...)` annotation, e.g. a full URL passed to `Router.tryRoutingTo`, which only takes a path
 * (plus optional query parameters and fragment).
 *
 * This applies to strings passed to annotated function parameters, as well as to strings compared against annotated
 * properties, e.g. `ctx.route.path == "/blog?sort=new"`, which can never be true, as `path` only holds a path.
 */
class InvalidRouteFormatInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)
                if (!expression.mayHaveRoutePathTarget()) return

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
