package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.LinkPath.UnknownSegmentsStrategy
import com.varabyte.kobweb.intellij.util.kobweb.route.findPages
import com.varabyte.kobweb.intellij.util.kobweb.route.findPublicResource
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoutePathParameter
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports routes that don't lead to any page or public resource, e.g. `Link("/blog/pots")`, which is usually a typo
 * or a link to a page that was moved or deleted.
 *
 * Only routes passed into parameters that take one are checked (e.g. `Link`'s `path`), and only if they could be
 * checked at all: routes with values that aren't known until runtime (e.g. `"/posts/$id"`) are only reported if none of
 * their possible values could lead anywhere.
 */
class UnknownRouteInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)
                if (expression.parent !is KtValueArgument) return
                val path = expression.linkPath ?: return
                // API routes are served by the backend, not by pages
                if (path.parts.first().text == "api") return

                // Unknown segments could hold anything at runtime, so only report paths that can't lead to any page
                val pages = expression.findPages()
                if (pages.any { page -> path.leadsTo(page.route, UnknownSegmentsStrategy.MATCH_ANYTHING) }) return
                if (expression.findPublicResource(path) != null) return

                // Checked last, as it's the most expensive check
                val parameter = analyze(expression) { expression.findRoutePathParameter() } ?: return
                if (!parameter.mustExist) return

                holder.registerProblem(
                    expression,
                    expression.linkPathRange(),
                    UxGlobals.textBundle.message("inspection.unknown.route.display.text"),
                )
            }
        }
    }
}
