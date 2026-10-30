package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.qualifiedPagesPackage
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedFunction

/** Kobweb's frontend `@file:PackageMapping` annotation, which changes the routes of the pages in a package. */
val PACKAGE_MAPPING_FQN = FqName("com.varabyte.kobweb.core.PackageMapping")

/**
 * Returns the resolver for routes of pages in this module, or null if this module isn't a Kobweb project (or we
 * can't tell where its pages live).
 */
private fun Module.findPageRouteResolver(): PageRouteResolver? {
    return CachedValuesManager.getManager(project).getCachedValue(this) {
        val pagesPackage = findKobwebProject()?.qualifiedPagesPackage
        CachedValueProvider.Result.create(
            pagesPackage?.let { PageRouteResolver(it, collectPackageMappings(it, RouteKind.PAGE)) },
            PsiModificationTracker.getInstance(project),
            ProjectRootModificationTracker.getInstance(project),
        )
    }
}

/**
 * Returns the resolver for routes of pages in this element's module, or null if it isn't in a Kobweb project (or we
 * can't tell where its pages live).
 */
fun PsiElement.findPageRouteResolver(): PageRouteResolver? = module?.findPageRouteResolver()

/**
 * Returns the route that Kobweb generates for [page], if it had the given route override (instead of its own).
 *
 * This is useful for comparing alternatives, e.g. to check if a route override is redundant.
 */
fun PageRouteResolver.resolve(page: KtNamedFunction, routeOverride: String?): PageRoute? {
    return resolve(page.containingKtFile.packageFqName, page.routeFileName, routeOverride)
}

/**
 * Returns the route that Kobweb generates for this page, e.g. "/blog/post" or "/users/{user}".
 *
 * See [PageRouteResolver] for details.
 *
 * @return null if this function isn't a page, if Kobweb rejects it.
 */
fun KtNamedFunction.findRoute(): PageRoute? {
    if (!isTopLevel) return null
    val pageAnnotation = findRouteAnnotation(RouteKind.PAGE) ?: return null
    val routeOverride = if (pageAnnotation.valueArguments.isEmpty()) null else {
        pageAnnotation.routeValue ?: return null
    }

    return findPageRouteResolver()?.resolve(this, routeOverride)
}
