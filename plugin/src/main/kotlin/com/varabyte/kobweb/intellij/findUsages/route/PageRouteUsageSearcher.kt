package com.varabyte.kobweb.intellij.findUsages.route

import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.Processor
import com.varabyte.kobweb.intellij.references.route.RouteReference
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.DynamicRouteSegment
import com.varabyte.kobweb.intellij.util.kobweb.route.PageRoute
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoute
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Text to search for when finding strings that lead to this route, e.g. "post" for "/blog/post".
 *
 * Checking whether a string leads to a route is relatively expensive, so instead of checking every string in the
 * project, we first ask IntelliJ for only the strings that contain this text, which it can answer quickly from its
 * index of the words in every file. Every string that leads to this route contains each of its static segments, so
 * this never misses one.
 *
 * We use the last static segment, as it usually tells a route apart from similar ones: every link under "/blog"
 * contains "blog", but only links to "/blog/post" contain "post".
 *
 * Returns null if the route has no static segments, e.g. "/" or "/{slug}", in which case every string has to be
 * checked.
 */
private val PageRoute.searchText: String?
    get() = segments.lastOrNull { segment -> segment.isNotEmpty() && DynamicRouteSegment.tryCreate(segment) == null }

/**
 * Calls [process] with every string in [scope] (in Kotlin files).
 */
private fun SearchScope.forEachString(process: (KtStringTemplateExpression) -> Unit) {
    val rootElements = when (this) {
        is LocalSearchScope -> scope.toList()
        is GlobalSearchScope -> {
            val psiManager = PsiManager.getInstance(project ?: return)
            FileTypeIndex.getFiles(KotlinFileType.INSTANCE, this).mapNotNull { file -> psiManager.findFile(file) }
        }
        else -> return
    }
    rootElements.forEach { root ->
        PsiTreeUtil.collectElementsOfType(root, KtStringTemplateExpression::class.java).forEach(process)
    }
}

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

        val visitedStrings = mutableSetOf<KtStringTemplateExpression>()
        fun processString(stringExpr: KtStringTemplateExpression) {
            if (!visitedStrings.add(stringExpr)) return
            stringExpr.references
                .filterIsInstance<RouteReference>()
                .filter { reference -> reference.isReferenceTo(page) }
                .forEach { reference -> processor.process(UsageInfo2UsageAdapter(UsageInfo(reference))) }
        }

        val searchText = route.searchText
        if (searchText != null) {
            PsiSearchHelper.getInstance(element.project).processElementsWithWord(
                { occurrence, _ ->
                    occurrence.parentOfType<KtStringTemplateExpression>(withSelf = true)?.let(::processString)
                    true
                },
                options.searchScope,
                searchText,
                UsageSearchContext.IN_STRINGS,
                /* caseSensitive = */ true,
            )
        } else {
            // Without any text to look up, we have to check every string, but routes without any static segments are
            // rare (e.g. the home page, "/")
            ReadAction.run<Throwable> { options.searchScope.forEachString(::processString) }
        }
    }

}
