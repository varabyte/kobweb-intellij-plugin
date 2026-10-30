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
class PageDefinition(
    override val fqn: String,
    override val route: PageRoute,
    val function: KtNamedFunction?,
) : RouteDefinition

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
                PageRoute(page.route),
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
 * This follows Kobweb's router: the path is sent through the site's [redirects] first (see [redirectedBy]), and only if
 * that doesn't match any page, it's tried again with a trailing slash added or removed (see
 * [LinkPath.withToggledTrailingSlash]), which is also sent through the redirects.
 *
 * If more than one page's route matches a path, this picks the same one Kobweb's router would: the one with static
 * segments in the earliest positions, e.g. "/posts/new" leads to "/posts/new" rather than "/posts/{post}".
 */
fun List<PageDefinition>.findLedToBy(path: LinkPath, redirects: List<RouteRedirect> = emptyList()): PageDefinition? {
    val matches = listOfNotNull(path, path.withToggledTrailingSlash())
        .map { candidate -> candidate.redirectedBy(redirects) }
        .firstNotNullOfOrNull { candidate -> filter { page -> candidate.matches(page.route) }.ifEmpty { null } }
        .orEmpty()

    return matches.pickMostSpecific { page -> page.route }
}
