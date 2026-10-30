package com.varabyte.kobweb.intellij.findUsages.route

import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.Processor
import com.varabyte.kobweb.intellij.references.route.RouteReference
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.PageRoute
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoute
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * When finding usages of a page, include the routes that lead to it, e.g. `Link("/blog/post")` for the page at
 * "/blog/post".
 *
 * NOTE: We intentionally do this with a [CustomUsageSearcher], which is only used by Find Usages, instead of a
 * `ReferencesSearch` executor, which is also used by Rename. A page's route comes from its file and package, not its
 * function's name, so renaming a page function shouldn't touch them. (See also: `RouteReference.handleElementRename`.)
 */
class PageRouteUsageSearcher : CustomUsageSearcher() {
    override fun processElementUsages(element: PsiElement, processor: Processor<in Usage>, options: FindUsagesOptions) {
        val (page, route) = ReadAction.compute<Pair<KtNamedFunction, PageRoute>?, Throwable> {
            val page = element as? KtNamedFunction ?: return@compute null
            if (!page.isInReadableKobwebProject()) return@compute null
            page.findRoute()?.let { route -> page to route }
        } ?: return

        route.processCandidateStrings(element.project, options.searchScope) { stringExpr ->
            stringExpr.references
                .filterIsInstance<RouteReference>()
                .filter { reference -> reference.isReferenceTo(page) }
                .forEach { reference -> processor.process(UsageInfo2UsageAdapter(UsageInfo(reference))) }
        }
    }
}
