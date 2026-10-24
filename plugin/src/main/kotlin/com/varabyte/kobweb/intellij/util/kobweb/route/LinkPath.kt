package com.varabyte.kobweb.intellij.util.kobweb.route

/**
 * The path part of a URL written in code that may point at a page, e.g. "/users/bob/posts" in
 * `Link("/users/bob/posts?sort=new")`.
 *
 * @param parts A list of [Part]s containing segment text values, e.g. ["users", "bob", "posts"], associated with the
 *   ranges where those text values were found inside their parent source route.
 */
class LinkPath private constructor(val parts: List<Part>) {
    private val segments: List<String?> = parts.map { it.text }

    /**
     * The piece of route between slashes.
     *
     * @property text The piece's text, with any escape sequences decoded, or null if it's a template expression, whose
     *   value can't be known until runtime, e.g. a template expression like "$id" in "/posts/$id".
     * @property sourceRange Where the piece was written in the source.
     */
    class Part(val text: String?, val sourceRange: IntRange)

    /**
     * Returns true if this path matches [route] exactly, e.g. "/users/bob/posts" matches "/users/{user}/posts".
     *
     * Segments with unknown values only match dynamic segments (see [UnknownSegmentsStrategy.MATCH_DYNAMIC]).
     *
     * See also [leadsTo], which also accounts for trailing slashes.
     */
    fun matches(route: PageRoute): Boolean = matches(route.segments, segments)

    /**
     * How segments whose values aren't known until runtime (e.g. the "$id" in "/posts/$id") are matched against a
     * route's segments.
     */
    enum class UnknownSegmentsStrategy {
        /**
         * Each unknown segment fills a single dynamic segment, which finds the route a path was most likely written
         * for, since links to pages with static routes are written out in full. For example, "/posts/$id" leads to
         * "/posts/{post}" but not "/posts/new".
         */
        MATCH_DYNAMIC,

        /**
         * An unknown segment could hold anything, including static text or even several segments, which finds every
         * route a path could possibly lead to. For example, "/posts/$id" might lead to "/posts/new" (if `id` is
         * "new"), and "/docs/$path" might lead to "/docs/a/b" (if `path` is "a/b").
         *
         * So only the segments before the first unknown one are checked, e.g. "/pots/$id" can't lead to "/posts/new".
         */
        MATCH_ANYTHING,
    }

    /**
     * Returns true if this path leads to a page served at [route], i.e. it [matches] the route, or it does if a
     * trailing slash is added or removed (e.g. "/blog" leads to "/blog/").
     *
     * This is because Kobweb's router adds or removes a trailing slash if that leads to a page and the path as written
     * doesn't.
     *
     * Paths without unknown segments lead to the same routes either way, but see [UnknownSegmentsStrategy] for how
     * [unknownSegmentsStrategy] affects the ones that have them.
     */
    fun leadsTo(
        route: PageRoute,
        unknownSegmentsStrategy: UnknownSegmentsStrategy = UnknownSegmentsStrategy.MATCH_DYNAMIC,
    ): Boolean {
        val firstUnknownIndex = segments.indexOf(null)
        if (unknownSegmentsStrategy == UnknownSegmentsStrategy.MATCH_ANYTHING && firstUnknownIndex >= 0) {
            return startsLike(route, segments.subList(0, firstUnknownIndex))
        }

        if (matches(route)) return true

        val toggledSlash = if (segments.lastOrNull() == "") segments.dropLast(1) else segments + ""
        return toggledSlash.isNotEmpty() && matches(route.segments, toggledSlash)
    }

    // True if the route could start with these (known) path segments, e.g. "/posts/{post}/edit" could start with
    // ["posts", "new"]
    private fun startsLike(route: PageRoute, knownSegments: List<String?>): Boolean {
        val routeSegments = route.segments
        for ((i, pathSegment) in knownSegments.withIndex()) {
            val routeSegment = routeSegments.getOrNull(i) ?: return false
            val dynamicSegment = DynamicRouteSegment.tryCreate(routeSegment)
            when {
                // A static segment, which only matches the same text
                dynamicSegment == null -> if (pathSegment != routeSegment) return false
                // A catch-all segment, which matches the rest of the path
                dynamicSegment.isCatchAll -> return true
                // Otherwise, a dynamic segment matches any non-empty segment
                pathSegment == "" -> return false
            }
        }
        return true
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
         * @param parts The pieces of source the URL was written in, in order.
         */
        fun parse(parts: List<Part>): LinkPath? {
            val first = parts.firstOrNull()?.text ?: return null
            if (!first.startsWith('/') || first.startsWith("//")) return null

            val segments = mutableListOf<String?>()
            val segmentRanges = mutableListOf<IntRange>()
            var segment: StringBuilder? = StringBuilder() // null while the current segment is unknown
            var segmentStart = parts.first().sourceRange.first
            var pathEnd = parts.last().sourceRange.last + 1
            loop@ for (part in parts) {
                val text = part.text
                if (text == null) {
                    segment = null
                    continue
                }
                val isVerbatim = text.length == part.sourceRange.count()
                for ((i, c) in text.withIndex()) {
                    val charOffset = part.sourceRange.first + i
                    val charRange = if (isVerbatim) charOffset..charOffset else part.sourceRange
                    when (c) {
                        // The path ends where the query or fragment begins
                        '?', '#' -> {
                            pathEnd = charRange.first
                            break@loop
                        }

                        '/' -> {
                            segments.add(segment?.toString())
                            segmentRanges.add(segmentStart until charRange.first)
                            segment = StringBuilder()
                            segmentStart = charRange.last + 1
                        }

                        else -> segment?.append(c)
                    }
                }
            }
            segments.add(segment?.toString())
            segmentRanges.add(segmentStart until pathEnd)

            // The path starts with a slash, which leaves an empty first segment behind
            fun List<Pair<String?, IntRange>>.toLinkPath(): LinkPath = LinkPath(this.map { Part(it.first, it.second) })
            return segments.drop(1).zip(segmentRanges.drop(1)).toLinkPath()
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
