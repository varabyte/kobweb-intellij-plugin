package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.route.GoToRedirectQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.ReplaceRedirectedRouteQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteRedirects
import com.varabyte.kobweb.intellij.util.kobweb.route.hasRoutePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import com.varabyte.kobweb.intellij.util.kobweb.route.mayHavePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.parseLinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.redirectedBy
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports routes that a redirect in the site's `.kobweb/conf.yaml` sends somewhere else, e.g. `Link("/old-page")` when
 * "/old-page" is redirected to "/new-page", and offers to replace them with where they end up.
 *
 * Redirects are meant for links from outside the site (e.g. bookmarks and search results), so links within the site's
 * own code may as well go straight to the new route. This also catches comparisons that can never be true, e.g.
 * `ctx.route.path == "/old-page"`, as the current route is the one the redirect sent the visitor to.
 */
class RedirectedRouteInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR
        val redirects = holder.file.findRouteRedirects()
        if (redirects.isEmpty()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)
                if (!expression.mayHavePathTarget()) return
                val path = expression.parseLinkPath() ?: return
                val redirectedPath = path.redirectedBy(redirects).takeIf { it !== path } ?: return
                // Checked last, as it's the most expensive check
                if (!analyze(expression) { expression.hasRoutePathTarget() }) return

                // Note: Any query parameters or fragment the redirect adds are left out, as the ones written here are
                // kept as is
                val newPath = redirectedPath.toString()
                holder.registerProblem(
                    expression,
                    expression.linkPathRange(),
                    UxGlobals.textBundle.message("inspection.redirected.route.display.text", newPath),
                    ReplaceRedirectedRouteQuickFix(newPath),
                    *listOfNotNull(GoToRedirectQuickFix.create(path, redirects)).toTypedArray(),
                )
            }
        }
    }
}
