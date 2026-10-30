package com.varabyte.kobweb.intellij.findUsages.api

import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.Processor
import com.varabyte.kobweb.intellij.findUsages.route.processCandidateStrings
import com.varabyte.kobweb.intellij.references.api.ApiPathReference
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiRoute
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiStreamRoute
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.Route
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

/**
 * When finding usages of an API endpoint, include the paths that lead to it, e.g. `window.api.get("hello")` for the
 * `@Api` function at "/hello", or `ApiStream("chat")` for the `ApiStream` property at "/chat".
 *
 * NOTE: We intentionally do this with a [CustomUsageSearcher], which is only used by Find Usages, instead of a
 * `ReferencesSearch` executor, which is also used by Rename. An endpoint's route comes from its file and package, not
 * its declaration's name, so renaming an endpoint shouldn't touch them. (See also: `ApiPathReference.handleElementRename`.)
 */
class ApiRouteUsageSearcher : CustomUsageSearcher() {
    override fun processElementUsages(element: PsiElement, processor: Processor<in Usage>, options: FindUsagesOptions) {
        val (endpoint, route) = ReadAction.compute<Pair<KtCallableDeclaration, Route>?, Throwable> {
            if (!element.isInReadableKobwebProject()) return@compute null
            val route = when (element) {
                is KtNamedFunction -> element.findApiRoute()
                is KtProperty -> analyze(element) { element.findApiStreamRoute() }
                else -> null
            }
            route?.let { (element as KtCallableDeclaration) to route }
        } ?: return

        route.processCandidateStrings(element.project, options.searchScope) { stringExpr ->
            stringExpr.references
                .filterIsInstance<ApiPathReference>()
                .filter { reference -> reference.isReferenceTo(endpoint) }
                .forEach { reference -> processor.process(UsageInfo2UsageAdapter(UsageInfo(reference))) }
        }
    }
}
