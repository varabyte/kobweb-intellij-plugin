package com.varabyte.kobweb.intellij.util.kobweb.route

/**
 * The path part of a URL written in code that may point at a page, e.g. "/users/bob/posts" in
 * `Link("/users/bob/posts?sort=new")`.
 *
 * @property segments The path's segments, e.g. ["users", "bob", "posts"]. A segment is null if its value can't be known
 *   until runtime, i.e. it contains a template expression (like "$id" in "/posts/$id").
 */
class LinkPath private constructor(val segments: List<String?>) {
    /**
     * Returns true if this path matches [route] exactly, e.g. "/users/bob/posts" matches "/users/{user}/posts".
     *
     * Segments with unknown values only match dynamic segments, since links to pages with static routes are written
     * out in full.
     *
     * See also [leadsTo], which also accounts for trailing slashes.
     */
    fun matches(route: String): Boolean = matches(route.removePrefix("/").split('/'), segments)

    /**
     * Returns true if this path leads to a page served at [route], i.e. it [matches] the route, or it does if a
     * trailing slash is added or removed (e.g. "/blog" leads to "/blog/").
     *
     * This is because Kobweb's router adds or removes a trailing slash if that leads to a page and the path as written
     * doesn't.
     */
    fun leadsTo(route: String): Boolean {
        if (matches(route)) return true

        val toggledSlash = if (segments.lastOrNull() == "") segments.dropLast(1) else segments + ""
        return toggledSlash.isNotEmpty() && matches(route.removePrefix("/").split('/'), toggledSlash)
    }

    override fun toString() = segments.joinToString("/", prefix = "/") { it ?: "?" }

    companion object {
        /**
         * Returns the path of a URL written in code, or null if it isn't an absolute path within the site, e.g. if it's
         * a relative path, has a host (e.g. "https://example.com" or "//example.com"), or is something else entirely
         * (e.g. "mailto:me@example.com").
         *
         * Any query parameters and fragment are ignored.
         *
         * @param parts The URL's text, in order. A null part is a template expression, whose value can't be known
         *   until runtime.
         */
        fun parse(parts: List<String?>): LinkPath? {
            val first = parts.firstOrNull() ?: return null
            if (!first.startsWith('/') || first.startsWith("//")) return null

            val segments = mutableListOf<String?>()
            var segment: StringBuilder? = StringBuilder() // null while the current segment is unknown
            loop@ for (part in parts) {
                if (part == null) {
                    segment = null
                    continue
                }
                for (c in part) {
                    when (c) {
                        // The path ends where the query or fragment begins
                        '?', '#' -> break@loop
                        '/' -> {
                            segments.add(segment?.toString())
                            segment = StringBuilder()
                        }

                        else -> segment?.append(c)
                    }
                }
            }
            segments.add(segment?.toString())

            // The path starts with a slash, which leaves an empty first segment behind
            return LinkPath(segments.drop(1))
        }

        // True if the path matches the route definition, e.g. route "/about" is matched by path "/about",
        // "/users/{user}/profile" is matched by "/users/me/profile", and "/users/{user}/profile" is also matched by
        // "/users/(null)/profile". If the route ends with a catch-all, that would consume all remaining path segments.
        private fun matches(routeSegments: List<String>, pathSegments: List<String?>): Boolean {
            // Each route segment matches the path segment at the same position, except for catch-all segments, which
            // match the rest of the path
            for ((i, routeSegment) in routeSegments.withIndex()) {
                val remainingPathSegments = pathSegments.drop(i)
                val dynamicSegment = DynamicRouteSegment.tryCreate(routeSegment)

                if (dynamicSegment == null) {
                    // A static segment, which only matches the same text
                    if (remainingPathSegments.isEmpty() || remainingPathSegments.first() != routeSegment) return false
                    continue
                }

                // Kobweb only allows catch-all and optional segments at the end of a route
                if (dynamicSegment.isCatchAll) {
                    // e.g. "/docs/{...path}" matches "/docs/a" and "/docs/a/b", but only "/docs/{...path?}" matches
                    // "/docs" (or "/docs/")
                    return dynamicSegment.isOptional || remainingPathSegments.any { it != "" }
                }

                // An optional segment also matches a missing or empty last segment, e.g. "/users/{user?}" matches
                // "/users" and "/users/"
                val isMissingOrEmpty = remainingPathSegments.isEmpty() || remainingPathSegments == listOf("")
                if (dynamicSegment.isOptional && isMissingOrEmpty) return true

                // Otherwise, a dynamic segment matches any non-empty segment (including ones whose value is unknown)
                if (remainingPathSegments.isEmpty() || remainingPathSegments.first() == "") return false
            }

            // Every route segment matched, so the path matches as long as it doesn't have any segments left over
            return pathSegments.size == routeSegments.size
        }
    }
}
