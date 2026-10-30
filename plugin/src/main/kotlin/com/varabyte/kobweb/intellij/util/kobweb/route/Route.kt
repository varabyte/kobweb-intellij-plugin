package com.varabyte.kobweb.intellij.util.kobweb.route

/**
 * A route that Kobweb registers something at, e.g. "/users/{user}" for a page or an API endpoint.
 *
 * Routes always start with a slash, and may contain dynamic segments (see [DynamicRouteSegment]). What a route means
 * depends on what's registered at it (e.g. API routes are relative to "/api"), so each kind has its own type, e.g.
 * [PageRoute].
 *
 * @property value The route, e.g. "/users/{user}".
 * @property kind The type of target endpoint this route refers to.
 */
interface Route {
    val value: String
    val kind: RouteKind
}

/**
 * What a route belongs to, which decides the rules that apply to it, e.g. which package it's relative to.
 */
enum class RouteKind {
    /** A page, i.e. an `@Page` function, e.g. "/blog/post". */
    PAGE,

    /** An API endpoint, i.e. an `@Api` function, e.g. "/users/{user}" (for `/api/users/{user}`). */
    API,

    /**
     * An API stream, i.e. an `ApiStream` property, e.g. "/chat". Streams share their routing rules (e.g. the api
     * package) with API endpoints, but are connected to by their exact route.
     */
    STREAM,
}

/**
 * This route as users write it, e.g. "/api/users/{user}" for an API endpoint, or "chat" for a stream (which is
 * connected to without a leading slash, e.g. `ApiStream("chat")`).
 */
val Route.displayValue: String
    get() = when (kind) {
        RouteKind.PAGE -> value
        RouteKind.API -> "/api$value"
        RouteKind.STREAM -> value.removePrefix("/")
    }

/**
 * A declaration that Kobweb registers at some route, e.g. a page or an API endpoint.
 *
 * @property fqn The fully qualified name of the declaration, e.g. "com.example.pages.blog.PostPage".
 * @property route The route the declaration is registered at.
 */
interface RouteDefinition {
    val fqn: String
    val route: Route
}

/**
 * The segments of this route, e.g. ["users", "{user}"] for "/users/{user}".
 *
 * Note that a trailing slash leaves an empty last segment, e.g. "/blog/" is ["blog", ""] (and "/" is [""]).
 */
val Route.segments: List<String> get() = value.removePrefix("/").split('/')

/** True if any segment of this route is dynamic, e.g. "/users/{user}". */
val Route.hasDynamicSegments: Boolean
    get() = segments.any { segment -> DynamicRouteSegment.tryCreate(segment) != null }

/**
 * Returns the item whose route Kobweb's router would pick, if more than one of them matches the same path: the one with
 * static segments in the earliest positions, e.g. "/posts/new" rather than "/posts/{post}" for "/posts/new".
 *
 * @param route The route of each item.
 */
fun <T> List<T>.pickMostSpecific(route: (T) -> Route): T? {
    // Rank each route segment, where lower ranks win: static segments, then dynamic ones
    fun T.segmentRanks() =
        route(this).segments.map { segment -> if (DynamicRouteSegment.tryCreate(segment) == null) 0 else 1 }

    return minWithOrNull { a, b ->
        val (aRanks, bRanks) = a.segmentRanks() to b.segmentRanks()
        aRanks.zip(bRanks).firstOrNull { (aRank, bRank) -> aRank != bRank }?.let { (aRank, bRank) -> aRank - bRank }
            ?: (aRanks.size - bRanks.size)
    }
}
