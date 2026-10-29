package com.varabyte.kobweb.intellij.util.kobweb.api

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.qualifiedApiPackage
import com.varabyte.kobweb.intellij.util.kobweb.route.DynamicRouteSegment
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteResolver
import com.varabyte.kobweb.intellij.util.kobweb.route.collectPackageMappings
import com.varabyte.kobweb.intellij.util.kobweb.route.routeValue
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

/** Kobweb's backend annotation for API endpoints, i.e. `@Api` functions and (optionally) `ApiStream` properties. */
val API_FQN = FqName("com.varabyte.kobweb.api.Api")

/** Kobweb's backend `@file:PackageMapping` annotation, which works like the frontend one, but for API routes. */
val API_PACKAGE_MAPPING_FQN = FqName("com.varabyte.kobweb.api.PackageMapping")

/**
 * Returns the resolver for routes of API endpoints in this module, or null if this module isn't a Kobweb project (or
 * we can't tell where its API endpoints live).
 */
private fun Module.findApiRouteResolver(): RouteResolver? {
    return CachedValuesManager.getManager(project).getCachedValue(this) {
        val apiPackage = findKobwebProject()?.qualifiedApiPackage
        CachedValueProvider.Result.create(
            apiPackage?.let { RouteResolver(it, collectPackageMappings(it, API_PACKAGE_MAPPING_FQN)) },
            PsiModificationTracker.getInstance(project),
            ProjectRootModificationTracker.getInstance(project),
        )
    }
}

/**
 * Returns the route that Kobweb serves this declaration at, relative to "/api", using its `@Api` annotation's route
 * override if it has one.
 *
 * @return null if Kobweb rejects it (or we can't tell its route).
 */
private fun KtCallableDeclaration.resolveApiRoute(): String? {
    val apiAnnotation = annotationEntries.firstOrNull { it.isAnnotationOfType(API_FQN) }
    val routeOverride = if (apiAnnotation == null || apiAnnotation.valueArguments.isEmpty()) null else {
        apiAnnotation.routeValue ?: return null
    }

    val fileName = FileUtilRt.getNameWithoutExtension(containingKtFile.name)
    return module?.findApiRouteResolver()?.resolve(containingKtFile.packageFqName, fileName, routeOverride)
}

/**
 * Returns the route that Kobweb serves this `@Api` function at, relative to "/api", e.g. "/hello" or "/users/{user}".
 *
 * @return null if this function isn't an API endpoint, or if Kobweb rejects it.
 */
fun KtNamedFunction.findApiRoute(): String? {
    if (!isTopLevel || annotationEntries.none { it.isAnnotationOfType(API_FQN) }) return null
    return resolveApiRoute()
}

/**
 * Returns the route that Kobweb serves this `ApiStream` property at, relative to "/api", e.g. "/chat".
 *
 * This doesn't check that the property actually is an `ApiStream`, which requires Kotlin analysis.
 *
 * @return null if Kobweb rejects this stream.
 */
fun KtProperty.findApiStreamRoute(): String? {
    if (!isTopLevel) return null
    val route = resolveApiRoute() ?: return null
    // Kobweb doesn't currently allow dynamic segments in stream routes
    if (route.split('/').any { segment -> DynamicRouteSegment.tryCreate(segment) != null }) return null
    return route
}
