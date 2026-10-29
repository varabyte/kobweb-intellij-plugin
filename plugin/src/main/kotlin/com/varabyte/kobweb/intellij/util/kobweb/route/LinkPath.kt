package com.varabyte.kobweb.intellij.util.kobweb.route

/**
 * The path part of a URL written in code that may point at a page, e.g. "/users/bob/posts" in
 * `Link("/users/bob/posts?sort=new")`.
 *
 * @property segments The path's segments, i.e. the pieces between slashes, e.g. "users", "bob", and "posts".
 */
class LinkPath private constructor(val segments: List<Segment>) {
    private val segmentTexts: List<String?> = segments.map { it.text }

    /** True if any of this path's segments can't be known until runtime, e.g. "/posts/$id". */
    val hasUnknownSegments: Boolean get() = null in segmentTexts

    /**
     * A piece of the path between slashes, e.g. "bob" in "/users/bob/posts".
     *
     * @property text The segment's text, with any escape sequences decoded, or null if its value can't be known until
     *   runtime, i.e. it contains a template expression (like "$id" in "/posts/$id").
     * @property sourceRange Where the segment was written in the code, relative to the start of the string.
     */
    class Segment(val text: String?, val sourceRange: IntRange) {
        init {
            // Slashes separate segments, and "?" or "#" ends the path
            require(text == null || text.none { c -> c == '/' || c == '?' || c == '#' }) {
                "A segment can't contain separators, but got \"$text\""
            }
        }
    }

    /**
     * A piece of a URL's string literal, for [parse] to read: a run of literal text, an escape sequence, or a template
     * expression. Pieces don't necessarily line up with segments: one piece of literal text can hold several segments
     * (e.g. "/users/bob/posts" is a single piece with three segments), and one segment can be made of several pieces
     * (e.g. the segment "post-$id" is the piece "post-" followed by the template expression "$id").
     *
     * @property text The piece's text, with any escape sequences decoded, or null if it's a template expression, whose
     *   value can't be known until runtime.
     * @property sourceRange Where the piece was written in the code, relative to the start of the string (see also
     *   [sourceRangeOf]).
     */
    class Part(val text: String?, val sourceRange: IntRange) {
        init {
            // Decoding escapes only ever shortens text, e.g. "\u002F" (6 characters) becomes "/"
            require(text == null || text.length <= sourceRange.count()) {
                "A part's text can't be longer than its source, but got \"$text\" for $sourceRange"
            }
        }

        /**
         * Returns where the character at [index] of [text] was written, e.g. 7..7 for the "b" in "/users/bob".
         *
         * Usually that's a single position, but the characters of a decoded escape sequence can't be told apart in
         * the code, so they all get its whole range, e.g. all six characters of "\u002F" for its "/".
         */
        fun sourceRangeOf(index: Int): IntRange {
            val isVerbatim = text?.length == sourceRange.count()
            return if (isVerbatim) (sourceRange.first + index).let { it..it } else sourceRange
        }
    }

    /**
     * Returns true if this path matches [route] exactly, e.g. "/users/bob/posts" matches "/users/{user}/posts".
     *
     * Segments with unknown values only match dynamic segments (see [UnknownSegmentsStrategy.MATCH_DYNAMIC]).
     *
     * See also [leadsTo], which also accounts for trailing slashes.
     */
    fun matches(route: Route): Boolean = matches(route.segments, segmentTexts)

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
        val firstUnknownIndex = segmentTexts.indexOf(null)
        if (unknownSegmentsStrategy == UnknownSegmentsStrategy.MATCH_ANYTHING && firstUnknownIndex >= 0) {
            return startsLike(route, segmentTexts.subList(0, firstUnknownIndex))
        }

        if (matches(route)) return true
        return withToggledTrailingSlash()?.matches(route) == true
    }

    /**
     * Returns this path with a trailing slash added, or removed if it has one, e.g. "/blog/" for "/blog" (and vice
     * versa), or null for "/", which has no other form.
     *
     * The added slash's empty segment is placed at the end of this path's source.
     */
    fun withToggledTrailingSlash(): LinkPath? {
        val toggled = if (segmentTexts.last() == "") {
            segments.dropLast(1)
        } else {
            val end = segments.last().sourceRange.last + 1
            segments + Segment("", end until end)
        }
        return LinkPath(toggled).takeIf { toggled.isNotEmpty() }
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

    override fun toString() = segmentTexts.joinToString("/", prefix = "/") { it ?: "?" }

    companion object {
        /**
         * Returns the path of a URL written in code, or null if it isn't a path within the site, e.g. if it has a
         * host (e.g. "https://example.com" or "//example.com"), or is something else entirely (e.g.
         * "mailto:me@example.com").
         *
         * By default, the path must be absolute (e.g. "/users/bob"), as a relative one (e.g. "users/bob") could lead
         * almost anywhere, depending on the current page. Some paths are always relative to a fixed root, though, e.g.
         * API paths, which are relative to "/api/". Pass [allowRelativePath] for those, to parse them as if they were
         * written from that root, e.g. "users/bob" as "/users/bob".
         *
         * Any query parameters and fragment are ignored.
         *
         * @param parts The pieces of the URL's string literal, in order.
         * @param allowRelativePath Whether the path may be relative. For example, API paths are always relative to a
         *   fixed root ("/api/"), so `window.api.get("users/bob")` and `window.api.get("/users/bob")` both lead to
         *   "/api/users/bob". In other words, Kobweb treats such paths the same whether or not they start with a
         *   slash. For the same reason, they may start with a template expression, as it doesn't matter whether its
         *   value (which we don't know yet) starts with a slash.
         */
        fun parse(parts: List<Part>, allowRelativePath: Boolean = false): LinkPath? {
            val first = parts.firstOrNull()?.text
            if (parts.isEmpty() || first?.startsWith("//") == true) return null

            val hasLeadingSlash = first?.startsWith('/') == true
            if (!allowRelativePath && !hasLeadingSlash) return null

            // A leading slash leaves an empty first segment behind
            val segments = parseSegments(parts)
            return LinkPath(if (hasLeadingSlash) segments.drop(1) else segments)
        }

        /** Splits the pieces of a path's string literal into its segments, up to any query or fragment. */
        private fun parseSegments(parts: List<Part>): List<Segment> {
            val segments = mutableListOf<Segment>()
            var segmentText: StringBuilder? = StringBuilder() // null while the current segment is unknown
            var segmentStart = parts.first().sourceRange.first
            var pathEnd = parts.last().sourceRange.last + 1
            fun addSegment(end: Int) = segments.add(Segment(segmentText?.toString(), segmentStart until end))
            loop@ for (part in parts) {
                val text = part.text
                if (text == null) {
                    segmentText = null
                    continue
                }
                for ((i, c) in text.withIndex()) {
                    val charRange = part.sourceRangeOf(i)
                    when (c) {
                        // The path ends where the query or fragment begins
                        '?', '#' -> {
                            pathEnd = charRange.first
                            break@loop
                        }

                        '/' -> {
                            addSegment(end = charRange.first)
                            segmentText = StringBuilder()
                            segmentStart = charRange.last + 1
                        }

                        else -> segmentText?.append(c)
                    }
                }
            }
            addSegment(end = pathEnd)
            return segments
        }

        /**
         * Returns the path of a URL that isn't written in code, e.g. one produced by a redirect (see [parse]).
         *
         * Its segments' source ranges are positions in [url].
         */
        fun parse(url: String): LinkPath? = parse(listOf(Part(url, url.indices)))

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
