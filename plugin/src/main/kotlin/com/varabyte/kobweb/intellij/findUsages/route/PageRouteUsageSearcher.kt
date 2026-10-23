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
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoute
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

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
        val (page, route) = ReadAction.compute<Pair<KtNamedFunction, String>?, Throwable> {
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

        val searchWord = findSearchWord(route)
        if (searchWord != null) {
            PsiSearchHelper.getInstance(element.project).processElementsWithWord(
                { occurrence, _ ->
                    occurrence.parentOfType<KtStringTemplateExpression>(withSelf = true)?.let(::processString)
                    true
                },
                options.searchScope,
                searchWord,
                UsageSearchContext.IN_STRINGS,
                /* caseSensitive = */ true,
            )
        } else {
            // Without a word to look up, we have to check every string, but routes without any words are rare (e.g.
            // the home page, "/")
            ReadAction.run<Throwable> { options.searchScope.forEachString(::processString) }
        }
    }

    companion object {
        /**
         * Returns a word from [route] to look up strings that might lead to it, e.g. "post" for "/blog/post".
         *
         * IntelliJ indexes the individual words in every string, so looking up one of a route's words quickly gives us
         * every string that contains it. Any string that leads to the route has to contain all of its words (except
         * for dynamic segments, which links fill in with other values), so whichever word we choose, we won't miss any
         * of them. From there, we can do the more expensive work of checking whether each string actually leads to the
         * route, on a much smaller set of strings.
         *
         * Which word we choose only affects how many unrelated strings we have to check. We use one from the last
         * static segment, as that's usually what tells a route apart from its siblings (e.g. every link under "/blog"
         * contains "blog", but only links to "/blog/post" contain "post").
         *
         * Returns null if the route doesn't have any words, e.g. "/" or "/{slug}".
         */
        @VisibleForTesting
        internal fun findSearchWord(route: String): String? {
            val lastStaticSegment = route.split('/')
                .lastOrNull { segment -> segment.isNotEmpty() && DynamicRouteSegment.tryCreate(segment) == null }
                ?: return null
            // A segment can hold more than one word, e.g. "blog-post" is indexed as "blog" and "post"
            return Regex("[A-Za-z0-9_]+").findAll(lastStaticSegment).map { it.value }.maxByOrNull { it.length }
        }
    }
}
