package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.route.GoToRedirectQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.DynamicRouteSegment
import com.varabyte.kobweb.intellij.util.kobweb.route.LinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoute
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteRedirects
import com.varabyte.kobweb.intellij.util.kobweb.route.redirectedBy
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports pages whose route a redirect in the site's `.kobweb/conf.yaml` always sends somewhere else, which means
 * Kobweb never shows them, e.g. a page served at "/old-page" when "/old-page" is redirected to "/new-page".
 *
 * This usually means the page was replaced, and either the page or the redirect was left behind by mistake.
 *
 * Only pages with static routes are checked, as there's no telling which values a redirect's pattern might match for
 * a dynamic segment.
 */
class RedirectedPageInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR
        val redirects = holder.file.findRouteRedirects()
        if (redirects.isEmpty()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                val route = function.findRoute() ?: return
                if (route.segments.any { segment -> DynamicRouteSegment.tryCreate(segment) != null }) return

                val path = LinkPath.parse(route.value) ?: return
                val redirectedPath = path.redirectedBy(redirects).takeIf { it !== path } ?: return
                holder.registerProblem(
                    function.nameIdentifier ?: return,
                    UxGlobals.textBundle.message(
                        "inspection.redirected.page.display.text",
                        route.value,
                        redirectedPath.toString(),
                    ),
                    *listOfNotNull(GoToRedirectQuickFix.create(path, redirects)).toTypedArray(),
                )
            }
        }
    }
}
