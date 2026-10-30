package com.varabyte.kobweb.intellij.util.kobweb.api

import com.varabyte.kobweb.intellij.util.kobweb.route.Route
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteKind

/**
 * The route that Kobweb serves an `@Api` function at, relative to "/api", e.g. "/hello" (for `/api/hello`), or
 * "/users/{user}" (for `/api/users/{user}`).
 */
@JvmInline
value class ApiRoute(override val value: String) : Route {
    override val kind get() = RouteKind.API

    override fun toString() = value
}

/**
 * The route that Kobweb registers an `ApiStream` at, e.g. "/chat".
 *
 * Unlike [ApiRoute]s, streams are connected to by their exact route (see [path]), so Kobweb rejects any with dynamic
 * segments.
 */
@JvmInline
value class ApiStreamRoute(override val value: String) : Route {
    override val kind get() = RouteKind.STREAM

    override fun toString() = value
}

/**
 * The path that connects to this stream, which is its route without the leading slash, e.g. "chat" in
 * `ApiStream("chat")`. (Kobweb removes any leading slash from both before comparing them.)
 */
val ApiStreamRoute.path: String get() = value.removePrefix("/")
