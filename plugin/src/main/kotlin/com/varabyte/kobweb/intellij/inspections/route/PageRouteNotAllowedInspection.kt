package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.RoutePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.findLedToBy
import com.varabyte.kobweb.intellij.util.kobweb.route.findPages
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
 * Reports routes that lead to a page where pages aren't allowed (see [RoutePathTarget.allowPages]), e.g.
 * `Image("/about")`.
 */
class PageRouteNotAllowedInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)
                if (!expression.mayHavePathTarget()) return
                val path = expression.parseLinkPath() ?: return

                val redirects = expression.findRouteRedirects()
                if (expression.findPages().findLedToBy(path, redirects) == null) return
                // The browser loads these paths from the server, which serves public files without routing to pages,
                // so a path that might lead to one is fine, even if it matches a page's route too (e.g. a catch-all)
                if (expression.mightLeadToPublicResource(path.redirectedBy(redirects))) return

                // Checked last, as it's the most expensive check
                val target = analyze(expression) { expression.findRoutePathTarget() } ?: return
                if (target.allowPages) return

                holder.registerProblem(
                    expression,
                    expression.linkPathRange(),
                    UxGlobals.textBundle.message("inspection.page.route.not.allowed.display.text"),
                )
            }
        }
    }
}
