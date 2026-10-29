package com.varabyte.kobweb.intellij.util.kobweb.api

import com.varabyte.kobweb.intellij.util.kobweb.route.Route

/**
 * The route that Kobweb serves an API endpoint (an `@Api` function or an `ApiStream`) at, relative to "/api", e.g.
 * "/hello" (for `/api/hello`), or "/users/{user}" (for `/api/users/{user}`).
 */
@JvmInline
value class ApiRoute(override val value: String) : Route {
    override fun toString() = value
}
