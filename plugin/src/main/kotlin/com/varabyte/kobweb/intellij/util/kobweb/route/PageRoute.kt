package com.varabyte.kobweb.intellij.util.kobweb.route

/**
 * The route that Kobweb serves a page at, e.g. "/blog/post" or "/users/{user}/posts".
 *
 * Routes always start with a slash. They may contain dynamic segments (see [DynamicRouteSegment]), and they end with a
 * slash if they're for an index page, e.g. "/blog/" for `pages/blog/Index.kt`.
 */
@JvmInline
value class PageRoute(val value: String) {
    /**
     * The segments of this route, e.g. ["blog", "post"] for "/blog/post".
     *
     * Note that a trailing slash leaves an empty last segment, e.g. "/blog/" is ["blog", ""] (and "/" is [""]).
     */
    val segments: List<String> get() = value.removePrefix("/").split('/')

    override fun toString() = value
}
