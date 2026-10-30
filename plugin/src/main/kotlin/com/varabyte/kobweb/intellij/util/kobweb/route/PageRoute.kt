package com.varabyte.kobweb.intellij.util.kobweb.route

/**
 * The route that Kobweb serves a page at, e.g. "/blog/post" or "/users/{user}/posts".
 *
 * Page routes end with a slash if they're for an index page, e.g. "/blog/" for `pages/blog/Index.kt`.
 */
@JvmInline
value class PageRoute(override val value: String) : Route {
    override val kind get() = RouteKind.PAGE

    override fun toString() = value
}
