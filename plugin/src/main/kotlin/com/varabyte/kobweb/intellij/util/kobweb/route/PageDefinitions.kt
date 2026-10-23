package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.varabyte.kobweb.intellij.util.kobweb.layout.PAGE_FQN
import com.varabyte.kobweb.intellij.util.kobweb.readKobwebFrontendMetadata
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * A page, and the route Kobweb serves it at.
 *
 * @property fqn The fully qualified name of the page function, e.g. "com.example.pages.blog.PostPage"
 * @property route The route the page is served at, e.g. "/blog/{post}".
 * @property function The page function's declaration. This can be null if a library advertises a page that we couldn't
 *   find a declaration for.
 */
class PageDefinition(val fqn: String, val route: String, val function: KtNamedFunction?)

private fun Module.collectPages(): List<PageDefinition> {
    // Kobweb includes the pages of any Kobweb modules this one depends on (e.g. a library in the same project)
    val sourcePages = KotlinAnnotationsIndex[PAGE_FQN.shortName().asString(), project, moduleWithDependenciesScope]
        .asSequence()
        .filter { entry -> entry.isAnnotationOfType(PAGE_FQN) }
        .mapNotNull { entry -> (entry.parent as? KtModifierList)?.parent as? KtNamedFunction }
        .mapNotNull { function ->
            val fqn = function.fqName?.asString() ?: return@mapNotNull null
            // Note: Each page's route is resolved using its own module's settings (e.g. its pages package)
            val route = function.findRoute() ?: return@mapNotNull null
            PageDefinition(fqn, route, function)
        }

    // Annotations in compiled dependencies aren't indexed, but Kobweb libraries tell us what pages they define
    val libraryScope = getModuleWithDependenciesAndLibrariesScope(false)
    val libraryPages = OrderEnumerator.orderEntries(this).recursively().librariesOnly().classes().roots
        .asSequence()
        .flatMap { root -> root.readKobwebFrontendMetadata()?.pages.orEmpty() }
        .map { page ->
            PageDefinition(
                page.fqn,
                page.route,
                KotlinTopLevelFunctionFqnNameIndex[page.fqn, project, libraryScope].firstOrNull(),
            )
        }

    return (sourcePages + libraryPages).distinctBy { it.fqn }.toList()
}

/**
 * Returns all pages visible to this element, i.e. those defined in its module plus any of its dependencies.
 */
fun PsiElement.findPages(): List<PageDefinition> {
    val module = module ?: return emptyList()
    return CachedValuesManager.getManager(project).getCachedValue(module) {
        CachedValueProvider.Result.create(
            module.collectPages(),
            PsiModificationTracker.getInstance(module.project),
            ProjectRootModificationTracker.getInstance(module.project),
        )
    }
}

/**
 * Returns the page that [path] leads to, or null if it doesn't lead to any of them.
 *
 * If more than one page's route matches a path, this picks the same one Kobweb's router would: the one with static
 * segments in the earliest positions, e.g. "/posts/new" leads to "/posts/new" rather than "/posts/{post}". (The router
 * also only adds or removes a trailing slash if the path as written doesn't match any page.)
 */
fun List<PageDefinition>.findLedToBy(path: LinkPath): PageDefinition? {
    val matches = filter { page -> path.matches(page.route) }.ifEmpty { filter { page -> path.leadsTo(page.route) } }

    // Rank each route segment, where lower ranks win: static segments, then dynamic ones
    fun PageDefinition.segmentRanks() =
        route.removePrefix("/").split('/').map { segment -> if (DynamicRouteSegment.tryCreate(segment) == null) 0 else 1 }

    return matches.minWithOrNull { a, b ->
        val (aRanks, bRanks) = a.segmentRanks() to b.segmentRanks()
        aRanks.zip(bRanks).firstOrNull { (aRank, bRank) -> aRank != bRank }?.let { (aRank, bRank) -> aRank - bRank }
            ?: (aRanks.size - bRanks.size)
    }
}
