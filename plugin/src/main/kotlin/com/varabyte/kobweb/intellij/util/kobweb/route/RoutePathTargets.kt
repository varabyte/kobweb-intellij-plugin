package com.varabyte.kobweb.intellij.util.kobweb.route

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

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
 * @property destination What the route is allowed to lead to, from `@RoutePath(destination = ...)`.
 */
class RoutePathTarget(
    val type: Type,
    override val mustExist: Boolean,
    val destination: Destination = Destination.PAGES_AND_RESOURCES,
) : PathTarget {
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

    /**
     * What a route may lead to, where a resource is anything the server responds to directly that isn't a page, e.g. a
     * public file or an API endpoint.
     *
     * Mirrors `RoutePathDestination` in Kobweb, whose entries have the same names.
     */
    enum class Destination {
        /** A page or a resource, e.g. `Link`'s `path`. */
        PAGES_AND_RESOURCES,

        /** Only a page, e.g. `Router.tryRoutingTo`'s path, as Kobweb's client router only knows about pages. */
        PAGES_ONLY,

        /**
         * Only a resource, e.g. `Image`'s `src`, as the browser loads it from the server, which serves resources
         * without ever routing to pages.
         */
        RESOURCES_ONLY,
    }
}

/** True if this route may lead to a page (see [RoutePathTarget.Destination]). */
val RoutePathTarget.allowsPages: Boolean get() = destination != RoutePathTarget.Destination.RESOURCES_ONLY

/** True if this route may lead to a resource, e.g. a public file (see [RoutePathTarget.Destination]). */
val RoutePathTarget.allowsResources: Boolean get() = destination != RoutePathTarget.Destination.PAGES_ONLY

/**
 * Hardcoded list of parameters that take a route in versions of Kobweb from before it annotated them with `@RoutePath`
 * (or that Kobweb can't annotate, as they live in modules that don't depend on it), by the function they belong to.
 */
private val IMPLICIT_ROUTE_PARAMETERS = mapOf(
    CallableId(FqName("com.varabyte.kobweb.silk.components.navigation"), Name.identifier("Link"))
        to ("path" to RoutePathTarget(RoutePathTarget.Type.URL, mustExist = true)),
    CallableId(KOBWEB_NAVIGATION_PACKAGE, Name.identifier("Anchor"))
        to ("href" to RoutePathTarget(RoutePathTarget.Type.URL, mustExist = true)),
    // Can also navigate to other sites
    CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("Router"), Name.identifier("navigateTo"))
        to ("pathQueryAndFragment" to RoutePathTarget(RoutePathTarget.Type.URL, mustExist = true)),
    CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("Router"), Name.identifier("tryRoutingTo"))
        to (
            "pathQueryAndFragment" to RoutePathTarget(
                RoutePathTarget.Type.PATH_QUERY_AND_FRAGMENT,
                mustExist = true,
                destination = RoutePathTarget.Destination.PAGES_ONLY,
            )
        ),
    // Can also load images from other sites
    CallableId(FqName("com.varabyte.kobweb.silk.components.graphics"), Name.identifier("Image"))
        to (
            "src" to RoutePathTarget(
                RoutePathTarget.Type.URL,
                mustExist = true,
                destination = RoutePathTarget.Destination.RESOURCES_ONLY,
            )
        ),
    // CSS's `url()`, e.g. `Modifier.backgroundImage(url("/images/cat.png"))`, which the browser loads from the server.
    // It lives in compose-html-ext, which doesn't depend on Kobweb core, so annotating it with `@RoutePath` is never
    // possible.
    CallableId(FqName("com.varabyte.kobweb.compose.css.functions"), Name.identifier("url"))
        to (
            "value" to RoutePathTarget(
                RoutePathTarget.Type.URL,
                mustExist = true,
                destination = RoutePathTarget.Destination.RESOURCES_ONLY,
            )
        ),
)

/**
 * Hardcoded list of property getters that return a route in versions of Kobweb from before it annotated them with
 * `@RoutePath`, i.e. what strings compared against them should look like.
 *
 * Kobweb marks these with `mustExist = false`, as they hold routes that might not lead to any page (e.g. the current
 * route, which might be a 404).
 */
private val IMPLICIT_ROUTE_GETTERS = buildMap {
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
private val IMPLICIT_ROUTE_SETTERS = mapOf(
    // Route interceptors redirect Kobweb's client router to the path they set, so it should lead to a page
    CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("RouteInterceptorScope"), Name.identifier("path"))
        to RoutePathTarget(
            RoutePathTarget.Type.PATH_ONLY,
            mustExist = true,
            destination = RoutePathTarget.Destination.PAGES_ONLY,
        ),
)

/**
 * Hardcoded list of parameters that take a route which their function returns again with something added, by the
 * function they belong to, e.g. `BasePath.prependTo`'s `path`, as it returns the route with the site's base path in
 * front of it.
 *
 * So strings passed into them inherit the target of wherever the result is used, e.g. the `"/images/cat.png"` in
 * `url(BasePath.prependTo("/images/cat.png"))` should lead to a resource, as `url` requires.
 *
 * Their own targets are like those of [IMPLICIT_ROUTE_PARAMETERS], for versions of Kobweb from before it annotated
 * them with `@RoutePath`.
 */
private val INHERITING_ROUTE_PARAMETERS = buildMap {
    // `BasePath.prependTo(...)` calls the companion object's override
    for (basePath in listOf(FqName("BasePath"), FqName("BasePath.Companion"))) {
        put(
            CallableId(KOBWEB_NAVIGATION_PACKAGE, basePath, Name.identifier("prependTo")),
            "path" to RoutePathTarget(RoutePathTarget.Type.PATH_QUERY_AND_FRAGMENT, mustExist = false),
        )
    }
    put(
        CallableId(KOBWEB_NAVIGATION_PACKAGE, Name.identifier("prependIf")),
        "path" to RoutePathTarget(RoutePathTarget.Type.PATH_QUERY_AND_FRAGMENT, mustExist = false),
    )
}

/**
 * Returns a target that only accepts routes that both this target and [other] accept, e.g. for a route that's used
 * with both of them.
 *
 * (If they allow different destinations, e.g. pages only and resources only, no route could satisfy both, so this one's
 * destination is kept.)
 */
private fun RoutePathTarget.intersect(other: RoutePathTarget): RoutePathTarget {
    val types = setOf(type, other.type)
    return RoutePathTarget(
        when {
            RoutePathTarget.Type.PATH_ONLY in types -> RoutePathTarget.Type.PATH_ONLY
            RoutePathTarget.Type.PATH_QUERY_AND_FRAGMENT in types -> RoutePathTarget.Type.PATH_QUERY_AND_FRAGMENT
            else -> RoutePathTarget.Type.URL
        },
        mustExist = mustExist || other.mustExist,
        destination = when (destination) {
            RoutePathTarget.Destination.PAGES_AND_RESOURCES -> other.destination
            else -> destination
        },
    )
}

/**
 * Returns the call whose result this expression inherits the target of (see [INHERITING_ROUTE_PARAMETERS]), if any,
 * e.g. `BasePath.prependTo("/blog")` for the `"/blog"` in it.
 */
context(kaSession: KaSession)
private fun KtExpression.findInheritingCall(): KtExpression? = with(kaSession) {
    val argument = parent as? KtValueArgument ?: return null
    val callElement = argument.getStrictParentOfType<KtCallElement>() as? KtCallExpression ?: return null
    val call = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return null
    val (parameterName, _) = INHERITING_ROUTE_PARAMETERS[call.symbol.callableId] ?: return null
    if (call.argumentMapping[this@findInheritingCall]?.symbol?.name?.asString() != parameterName) return null
    return callElement.getQualifiedExpressionForSelectorOrThis()
}

/**
 * Kobweb's `@RoutePath` annotation, along with the declarations older versions of Kobweb didn't annotate yet.
 */
private val ROUTE_PATH_ANNOTATION = PathAnnotation(
    ROUTE_PATH_CLASS_ID,
    toPathTarget = { routePath ->
        RoutePathTarget(
            routePath.readEnumArgument("type", default = RoutePathTarget.Type.URL),
            mustExist = routePath.readBooleanArgument("mustExist", default = true),
            destination = routePath.readEnumArgument(
                "destination",
                default = RoutePathTarget.Destination.PAGES_AND_RESOURCES,
            ),
        )
    },
    implicitParameters = IMPLICIT_ROUTE_PARAMETERS + INHERITING_ROUTE_PARAMETERS,
    implicitGetters = IMPLICIT_ROUTE_GETTERS,
    implicitSetters = IMPLICIT_ROUTE_SETTERS,
)

/**
 * Returns what this expression (usually a string) is used with if that holds a route (see [RoutePathTarget]), e.g.
 * `Link`'s `path` parameter for the `"/blog"` in `Link("/blog")`.
 *
 * Such parameters and properties are annotated with Kobweb's `@RoutePath` (which also lets users mark their own),
 * although older versions of Kobweb didn't have it, so we also recognize the declarations it was later added to, e.g.
 * `ctx.route.path`.
 *
 * If this string is passed through a call that returns the same route with something added (e.g. `BasePath.prependTo`),
 * the target also includes whatever the call's result is used with, e.g. `url`'s for the `"/images/cat.png"` in
 * `url(BasePath.prependTo("/images/cat.png"))` (see [INHERITING_ROUTE_PARAMETERS]).
 *
 * Call [mayHavePathTarget] first to skip strings that can't have one without running analysis.
 *
 * This method must be called inside an `analyze` block.
 */
context(_: KaSession)
fun KtExpression.findRoutePathTarget(): RoutePathTarget? {
    val target = findPathTarget(ROUTE_PATH_ANNOTATION)
    val inheritedTarget = findInheritingCall()?.findRoutePathTarget() ?: return target
    return target?.intersect(inheritedTarget) ?: inheritedTarget
}

/**
 * Returns true if this string is used with something that holds a route (see [findRoutePathTarget]).
 *
 * This method must be called inside an `analyze` block.
 */
context(_: KaSession)
fun KtStringTemplateExpression.hasRoutePathTarget(): Boolean = findRoutePathTarget() != null
