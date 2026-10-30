package com.varabyte.kobweb.intellij.util.kobweb.api

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.qualifiedApiPackage
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteKind
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteResolver
import com.varabyte.kobweb.intellij.util.kobweb.route.collectPackageMappings
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteAnnotation
import com.varabyte.kobweb.intellij.util.kobweb.route.hasDynamicSegments
import com.varabyte.kobweb.intellij.util.kobweb.route.routeFileName
import com.varabyte.kobweb.intellij.util.kobweb.route.routeValue
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.isPublic

/** Kobweb's backend annotation for API endpoints, i.e. `@Api` functions and (optionally) `ApiStream` properties. */
val API_FQN = FqName("com.varabyte.kobweb.api.Api")

/** Kobweb's backend class for API streams, which public, top-level properties of exactly this type are registered as. */
val API_STREAM_CLASS_ID = ClassId(FqName("com.varabyte.kobweb.api.stream"), Name.identifier("ApiStream"))

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
            apiPackage?.let { RouteResolver(it, collectPackageMappings(it, RouteKind.API)) },
            PsiModificationTracker.getInstance(project),
            ProjectRootModificationTracker.getInstance(project),
        )
    }
}

/**
 * Returns the resolver for routes of API endpoints in this element's module, or null if it isn't in a Kobweb project
 * (or we can't tell where its API endpoints live).
 */
fun PsiElement.findApiRouteResolver(): RouteResolver? = module?.findApiRouteResolver()

/**
 * Returns the route that Kobweb would register this declaration at, as an API endpoint or stream, using its `@Api`
 * annotation's route override if it has one, e.g. "/users/{user}" (for `/api/users/{user}`).
 *
 * @return null if Kobweb rejects it (or we can't tell its route).
 */
private fun KtCallableDeclaration.resolveApiRouteValue(): String? {
    val apiAnnotation = findRouteAnnotation(RouteKind.API)
    val routeOverride = if (apiAnnotation == null || apiAnnotation.valueArguments.isEmpty()) null else {
        apiAnnotation.routeValue ?: return null
    }

    return module?.findApiRouteResolver()?.resolve(containingKtFile.packageFqName, routeFileName, routeOverride)
}

/**
 * Returns the route that Kobweb would serve this declaration at as an API endpoint (see [resolveApiRouteValue]).
 *
 * Unlike [findApiRoute], this doesn't check that the declaration is an `@Api` function.
 */
fun KtCallableDeclaration.resolveApiRoute(): ApiRoute? = resolveApiRouteValue()?.let(::ApiRoute)

/**
 * Returns the route that Kobweb would register this declaration at as an API stream (see [resolveApiRouteValue]).
 *
 * Unlike [findApiStreamRoute], this doesn't check that the declaration is an `ApiStream` that Kobweb registers, or
 * that its route has no dynamic segments.
 */
fun KtCallableDeclaration.resolveApiStreamRoute(): ApiStreamRoute? = resolveApiRouteValue()?.let(::ApiStreamRoute)

/**
 * Returns the route that Kobweb serves this `@Api` function at, relative to "/api", e.g. "/hello" or "/users/{user}".
 *
 * @return null if this function isn't an API endpoint, or if Kobweb rejects it.
 */
fun KtNamedFunction.findApiRoute(): ApiRoute? {
    if (!isTopLevel || findRouteAnnotation(RouteKind.API) == null) return null
    return resolveApiRoute()
}

/**
 * Returns the route that Kobweb registers this `ApiStream` property at, e.g. "/chat".
 *
 * This method must be called inside an `analyze` block.
 *
 * @return null if this property isn't an `ApiStream` that Kobweb registers (i.e. a public, top-level property whose
 *   type is exactly `ApiStream`, e.g. `val chat = object : ApiStream() { ... }`), or if Kobweb rejects its route.
 */
context(_: KaSession)
fun KtProperty.findApiStreamRoute(): ApiStreamRoute? {
    if (!isTopLevel || !isPublic) return null
    val route = resolveApiStreamRoute() ?: return null
    // Kobweb doesn't currently allow dynamic segments in stream routes
    if (route.hasDynamicSegments) return null
    // Checked last, as it's the only check that needs analysis
    if (!isRegisteredApiStream()) return null
    return route
}

/**
 * False if this property clearly isn't an `ApiStream`, without needing analysis (unlike [isRegisteredApiStream]), as
 * it never mentions the class by name.
 *
 * This is how streams are found in the first place (by searching for files that mention `ApiStream`), so it's a cheap
 * way to skip most properties before checking them for real.
 */
val KtProperty.mightBeApiStream: Boolean
    get() = API_STREAM_CLASS_ID.shortClassName.asString() in text

/**
 * True if this property is an `ApiStream` that Kobweb registers, i.e. a public, top-level property whose type is
 * exactly `ApiStream`, wherever it lives and whatever its route.
 *
 * This method must be called inside an `analyze` block.
 */
context(kaSession: KaSession)
fun KtProperty.isRegisteredApiStream(): Boolean = with(kaSession) {
    isTopLevel && isPublic && (returnType as? KaClassType)?.classId == API_STREAM_CLASS_ID
}
