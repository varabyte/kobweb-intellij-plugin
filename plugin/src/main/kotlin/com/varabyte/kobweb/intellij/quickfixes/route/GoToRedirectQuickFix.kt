package com.varabyte.kobweb.intellij.quickfixes.route

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.project.Project
import com.varabyte.kobweb.intellij.util.kobweb.route.LinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteRedirect
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteRedirects
import com.varabyte.kobweb.intellij.util.ux.UxGlobals

/**
 * Open the site's `.kobweb/conf.yaml` with a redirect selected, e.g. the one that sends a page's route elsewhere.
 *
 * This doesn't directly fix anything, but by making it easier for the user to find the redirect, it's possible they can
 * then edit it themselves, potentially fixing a problem.
 *
 * @param redirectIndex The redirect's position in the site's redirects (see [findRouteRedirects]). This is stored
 *   instead of the redirect itself, as quick fixes shouldn't hold on to PSI elements.
 */
class GoToRedirectQuickFix(private val redirectIndex: Int) : ModCommandQuickFix(), PriorityAction {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.go_to_redirect.family.name")

    override fun getPriority() = PriorityAction.Priority.LOW

    override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
        val source = descriptor.psiElement.findRouteRedirects().getOrNull(redirectIndex)?.source
            ?.takeIf { it.isValid }
            ?: return ModCommand.nop()
        return ModCommand.select(source)
    }

    companion object {
        /**
         * Returns a fix that goes to the first of [redirects] that applies to [path], or null if none do (or it wasn't
         * read from a file, so there's nowhere to go).
         *
         * If redirects are chained, the first one is the one that matches the path as written, and the rest of the
         * chain is usually nearby. We don't return more than that because showing too much information can be a
         * confusing experience to the user (especially as quickfixes are sorted by name).
         */
        fun create(path: LinkPath, redirects: List<RouteRedirect>): GoToRedirectQuickFix? {
            val pathText = path.toString()
            val index = redirects.indexOfFirst { redirect -> redirect.map(pathText) != null }
            return GoToRedirectQuickFix(index).takeIf { index >= 0 && redirects[index].source != null }
        }
    }
}
