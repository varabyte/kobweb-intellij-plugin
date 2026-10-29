package com.varabyte.kobweb.intellij.util.kobweb.route

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

private val KOBWEB_CORE_PACKAGE = FqName("com.varabyte.kobweb.core")
private val KOBWEB_NAVIGATION_PACKAGE = FqName("com.varabyte.kobweb.navigation")

/**
 * Kobweb's annotation for parameters and properties that hold a route, e.g. `Link`'s `path`.
 */
private val ROUTE_PATH_CLASS_ID = ClassId(KOBWEB_CORE_PACKAGE, Name.identifier("RoutePath"))

/**
 * A parameter or property that holds a route, e.g. `Link`'s `path` parameter, or the `ctx.route.path` property. Strings
 * used with it (e.g. passed into it, compared against it, or assigned to it) should be one, e.g. "/blog" in
 * `Link("/blog")`, `ctx.route.path == "/blog"`, or `path = "/blog"`.
 *
 * @property type What the target accepts, from `@RoutePath(type = ...)`.
 * @property mustExist Whether the route should lead to a page (or another resource the site serves). False for targets
 *   marked `@RoutePath(mustExist = false)`, e.g. parameters that build links to pages that may not exist yet.
 */
class RoutePathTarget(val type: Type, val mustExist: Boolean) {
    /**
     * What sort of route format is accepted.
     *
     * Mirrors `RoutePathType` in Kobweb, whose entries have the same names.
     */
    enum class Type {
        /** Any URL, including ones to other sites (e.g. "https://example.com"). */
        URL,

        /** A path, optionally followed by query parameters and a fragment, e.g. "/blog/post?sort=new#comments". */
        PATH_QUERY_AND_FRAGMENT,

        /** Just a path, e.g. "/blog/post". */
        PATH_ONLY,
    }
}

/**
 * Hardcoded list of parameters that take a route in versions of Kobweb from before it annotated them with `@RoutePath`,
 * by the function they belong to.
 */
private val LEGACY_ROUTE_PARAMETERS = mapOf(
    CallableId(FqName("com.varabyte.kobweb.silk.components.navigation"), Name.identifier("Link"))
        to ("path" to RoutePathTarget(RoutePathTarget.Type.URL, mustExist = true)),
    CallableId(KOBWEB_NAVIGATION_PACKAGE, Name.identifier("Anchor"))
        to ("href" to RoutePathTarget(RoutePathTarget.Type.URL, mustExist = true)),
    // Can also navigate to other sites
    CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("Router"), Name.identifier("navigateTo"))
        to ("pathQueryAndFragment" to RoutePathTarget(RoutePathTarget.Type.URL, mustExist = true)),
    CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("Router"), Name.identifier("tryRoutingTo"))
        to ("pathQueryAndFragment" to RoutePathTarget(RoutePathTarget.Type.PATH_QUERY_AND_FRAGMENT, mustExist = true)),
)

/**
 * Hardcoded list of property getters that return a route in versions of Kobweb from before it annotated them with
 * `@RoutePath`, i.e. what strings compared against them should look like.
 *
 * Kobweb marks these with `mustExist = false`, as they hold routes that might not lead to any page (e.g. the current
 * route, which might be a 404).
 */
private val LEGACY_ROUTE_GETTERS = buildMap {
    // `RouteInfo` was nested inside `PageContext` in older versions of Kobweb
    for (routeInfo in listOf(FqName("RouteInfo"), FqName("PageContext.RouteInfo"))) {
        put(
            CallableId(KOBWEB_CORE_PACKAGE, routeInfo, Name.identifier("path")),
            RoutePathTarget(RoutePathTarget.Type.PATH_ONLY, mustExist = false),
        )
        put(
            CallableId(KOBWEB_CORE_PACKAGE, routeInfo, Name.identifier("pathQueryAndFragment")),
            RoutePathTarget(RoutePathTarget.Type.PATH_QUERY_AND_FRAGMENT, mustExist = false),
        )
    }
    put(
        CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("RouteInterceptorScope"), Name.identifier("path")),
        RoutePathTarget(RoutePathTarget.Type.PATH_ONLY, mustExist = false),
    )
    put(
        CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("RouteInterceptorScope"), Name.identifier("pathQueryAndFragment")),
        RoutePathTarget(RoutePathTarget.Type.PATH_QUERY_AND_FRAGMENT, mustExist = false),
    )
}

/**
 * Hardcoded list of property setters that take a route in versions of Kobweb from before it annotated them with
 * `@RoutePath`, i.e. what strings assigned to them should look like.
 */
private val LEGACY_ROUTE_SETTERS = mapOf(
    // Route interceptors redirect to the path they set, so it should lead somewhere
    CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("RouteInterceptorScope"), Name.identifier("path"))
        to RoutePathTarget(RoutePathTarget.Type.PATH_ONLY, mustExist = true),
)

/**
 * Kobweb's `@RoutePath` annotation, along with the declarations older versions of Kobweb didn't annotate yet.
 */
private val ROUTE_PATH_ANNOTATION = PathAnnotation(
    ROUTE_PATH_CLASS_ID,
    read = { routePath ->
        val type = (routePath.findArgument("type") as? KaAnnotationValue.EnumEntryValue)
            ?.callableId?.callableName?.asString()
            ?.let { typeName -> RoutePathTarget.Type.entries.firstOrNull { it.name == typeName } }
            ?: RoutePathTarget.Type.URL
        RoutePathTarget(type, routePath.readBooleanArgument("mustExist", default = true))
    },
    legacyParameters = LEGACY_ROUTE_PARAMETERS,
    legacyGetters = LEGACY_ROUTE_GETTERS,
    legacySetters = LEGACY_ROUTE_SETTERS,
)

/**
 * Returns what this string is used with if that holds a route (see [RoutePathTarget]), e.g. `Link`'s `path` parameter
 * for the `"/blog"` in `Link("/blog")`.
 *
 * Such parameters and properties are annotated with Kobweb's `@RoutePath` (which also lets users mark their own),
 * although older versions of Kobweb didn't have it, so we also recognize the declarations it was later added to, e.g.
 * `ctx.route.path`.
 *
 * Call [mayHavePathTarget] first to skip strings that can't have one without running analysis.
 *
 * This method must be called inside an `analyze` block.
 */
context(_: KaSession)
fun KtStringTemplateExpression.findRoutePathTarget(): RoutePathTarget? = findPathTarget(ROUTE_PATH_ANNOTATION)

/**
 * Returns true if this string is used with something that holds a route (see [findRoutePathTarget]).
 *
 * This method must be called inside an `analyze` block.
 */
context(_: KaSession)
fun KtStringTemplateExpression.hasRoutePathTarget(): Boolean = findRoutePathTarget() != null
