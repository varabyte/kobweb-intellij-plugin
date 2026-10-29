package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.LinkPath.UnknownSegmentsStrategy
import com.varabyte.kobweb.intellij.util.kobweb.route.findLedToBy
import com.varabyte.kobweb.intellij.util.kobweb.route.findPages
import com.varabyte.kobweb.intellij.util.kobweb.route.findPublicResource
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoutePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteRedirects
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import com.varabyte.kobweb.intellij.util.kobweb.route.mayHavePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.mightBeRedirectedBy
import com.varabyte.kobweb.intellij.util.kobweb.route.parseLinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.redirectedBy
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports routes that don't lead to any page or public resource, e.g. `Link("/blog/pots")`, which is usually a typo
 * or a link to a page that was moved or deleted.
 *
 * Only routes used with something that holds one are checked (e.g. `Link`'s `path`), and only if they could be
 * checked at all: routes with values that aren't known until runtime (e.g. `"/posts/$id"`) are only reported if none of
 * their possible values could lead anywhere.
 *
 * Routes are checked after any redirects in the site's `.kobweb/conf.yaml` (see [redirectedBy]), so a link to an old
 * page that's redirected to a new one is fine (though see [RedirectedRouteInspection]).
 */
class UnknownRouteInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)
                if (!expression.mayHavePathTarget()) return
                val path = expression.parseLinkPath() ?: return
                // API routes are served by the backend, not by pages
                if (path.segments.first().text == "api") return

                val pages = expression.findPages()
                val redirects = expression.findRouteRedirects()
                if (path.hasUnknownSegments) {
                    // Unknown segments could hold anything at runtime, so only report paths that can't lead to any
                    // page, even after a redirect
                    if (pages.any { page -> path.leadsTo(page.route, UnknownSegmentsStrategy.MATCH_ANYTHING) }) return
                    if (path.mightBeRedirectedBy(redirects)) return
                } else {
                    if (pages.findLedToBy(path, redirects) != null) return
                    if (expression.findPublicResource(path.redirectedBy(redirects)) != null) return
                }

                // Checked last, as it's the most expensive check
                val target = analyze(expression) { expression.findRoutePathTarget() } ?: return
                if (!target.mustExist) return

                holder.registerProblem(
                    expression,
                    expression.linkPathRange(),
                    UxGlobals.textBundle.message("inspection.unknown.route.display.text"),
                )
            }
        }
    }
}
