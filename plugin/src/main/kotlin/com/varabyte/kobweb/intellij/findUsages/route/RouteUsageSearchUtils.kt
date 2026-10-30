package com.varabyte.kobweb.intellij.findUsages.route

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.varabyte.kobweb.intellij.util.kobweb.route.DynamicRouteSegment
import com.varabyte.kobweb.intellij.util.kobweb.route.Route
import com.varabyte.kobweb.intellij.util.kobweb.route.segments
import org.jetbrains.kotlin.idea.KotlinFileType
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
private val Route.searchText: String?
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
 * Calls [process] (in a read action) with every string in [scope] that might lead to this route, each at most once.
 *
 * This only narrows down the strings to check (see [searchText]), so [process] still needs to check whether each one
 * actually leads to this route, e.g. by resolving its reference.
 */
internal fun Route.processCandidateStrings(
    project: Project,
    scope: SearchScope,
    process: (KtStringTemplateExpression) -> Unit
) {
    val visitedStrings = mutableSetOf<KtStringTemplateExpression>()
    fun processString(stringExpr: KtStringTemplateExpression) {
        if (visitedStrings.add(stringExpr)) process(stringExpr)
    }

    val searchText = searchText
    if (searchText != null) {
        PsiSearchHelper.getInstance(project).processElementsWithWord(
            { occurrence, _ ->
                occurrence.parentOfType<KtStringTemplateExpression>(withSelf = true)?.let(::processString)
                true
            },
            scope,
            searchText,
            UsageSearchContext.IN_STRINGS,
            /* caseSensitive = */ true,
        )
    } else {
        // Without any text to look up, we have to check every string, but routes without any static segments are rare
        // (e.g. the home page, "/")
        ReadAction.run<Throwable> { scope.forEachString(::processString) }
    }
}
