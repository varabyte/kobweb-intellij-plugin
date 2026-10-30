package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.RoutePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.allowsPages
import com.varabyte.kobweb.intellij.util.kobweb.route.allowsResources
import com.varabyte.kobweb.intellij.util.kobweb.route.findLedToBy
import com.varabyte.kobweb.intellij.util.kobweb.route.findPages
import com.varabyte.kobweb.intellij.util.kobweb.route.findPublicResource
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoutePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteRedirects
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import com.varabyte.kobweb.intellij.util.kobweb.route.mayHavePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.mightLeadToPublicResource
import com.varabyte.kobweb.intellij.util.kobweb.route.parseLinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.redirectedBy
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports routes that lead to something they aren't allowed to (see [RoutePathTarget.Target]), e.g. a page in
 * `Image("/about")`, or a public file in `router.tryRoutingTo("/resume.pdf")`.
 */
class DisallowedRouteTargetInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)
                if (!expression.mayHavePathTarget()) return
                val path = expression.parseLinkPath() ?: return

                val redirects = expression.findRouteRedirects()
                val redirectedPath = path.redirectedBy(redirects)
                val leadsToPage = expression.findPages().findLedToBy(path, redirects) != null

                // Where only resources are allowed, the browser loads the path from the server, which serves resources
                // without ever routing to pages, so a path that might lead to one is fine, even if it matches a page's
                // route too (e.g. a catch-all)
                val mayBeDisallowedPage = leadsToPage && !expression.mightLeadToPublicResource(redirectedPath)
                // Where only pages are allowed, Kobweb's router shows a page if one matches, so only paths that don't
                // lead to a page but do lead to a resource are a problem. Paths with segments that aren't known until
                // runtime might lead to a page, so they aren't checked.
                val mayBeDisallowedResource = !leadsToPage && !path.hasUnknownSegments && (
                    // Pages can't live under "/api", which Kobweb reserves for API endpoints
                    path.segments.first().text == "api" || expression.findPublicResource(redirectedPath) != null
                )
                if (!mayBeDisallowedPage && !mayBeDisallowedResource) return

                // Checked last, as it's the most expensive check
                val target = analyze(expression) { expression.findRoutePathTarget() } ?: return
                val messageKey = when {
                    mayBeDisallowedPage && !target.allowsPages -> "inspection.disallowed.route.target.page.display.text"
                    mayBeDisallowedResource && !target.allowsResources ->
                        "inspection.disallowed.route.target.resource.display.text"
                    else -> return
                }

                holder.registerProblem(expression, expression.linkPathRange(), UxGlobals.textBundle.message(messageKey))
            }
        }
    }
}
