package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.openapi.util.TextRange

/**
 * A problem with a value that Kobweb builds routes from, i.e. a `@Page` route override or a `@file:PackageMapping`.
 *
 * @property range Where the problem is, within the value.
 */
class RouteValueProblem(val kind: Kind, val range: TextRange) {
    /**
     * True if Kobweb fails the build over this problem, or false if it only warns about it.
     */
    val isError get() = kind != Kind.MULTIPLE_SEGMENTS

    enum class Kind {
        /**
         * An inferred (e.g. "{}"), catch-all (e.g. "{...rest}"), or optional (e.g. "{id?}") dynamic segment somewhere
         * other than the end of a route, which Kobweb doesn't allow.
         *
         * Package mappings are never at the end of a route, so they can never use catch-all or optional segments
         * (although their last segment can be inferred).
         */
        MISPLACED_DYNAMIC_SEGMENT,

        /**
         * A dynamic segment whose name has characters that can't be part of one, e.g. "{rest...}" or "{a b}". (Names can
         * only contain letters, digits, "-", and "_".)
         */
        MALFORMED_DYNAMIC_SEGMENT,

        /** A segment with curly braces that isn't a dynamic segment at all, e.g. "{post" or "a{b}c". */
        STRAY_CURLY_BRACES,

        /** An empty segment, e.g. in "a//b". */
        EMPTY_SEGMENT,

        /**
         * A "." or ".." segment (or a percent-encoded form, e.g. "%2e"), which browsers remove from URLs (resolving
         * them like relative directories), so a route with one can never match.
         */
        DOT_SEGMENT,

        /**
         * A segment with characters that can't be part of a URL path as written, e.g. "about us" (see
         * [findInvalidCharacters]).
         */
        INVALID_CHARACTER,

        /**
         * A package mapping with a slash in it, e.g. "a/b", which adds more than one segment to routes instead of
         * replacing the package's one.
         */
        MULTIPLE_SEGMENTS,
    }

    companion object {
        /**
         * Returns the problems with a `@Page` annotation's route override, e.g. "/blog/{}".
         */
        fun findInRouteOverride(routeOverride: String): List<RouteValueProblem> {
            return findIn(routeOverride) { segment, isLast ->
                // Only the last segment can be inferred, a catch-all, or optional (and if the override ends with a
                // slash, its last segment is the empty string after it, as the page's slug comes next)
                !isLast && (segment.isInferred || segment.isCatchAll || segment.isOptional)
            }
        }

        /**
         * Returns the problems with a `@file:PackageMapping` annotation's value, e.g. "{}".
         *
         * Kobweb ignores a package mapping with any problem that's an error (see [RouteValueProblem.isError]).
         */
        fun findInPackageMapping(packageMapping: String): List<RouteValueProblem> {
            val multipleSegments = RouteValueProblem(Kind.MULTIPLE_SEGMENTS, TextRange.allOf(packageMapping))
                .takeIf { '/' in packageMapping }
            return listOfNotNull(multipleSegments) + findIn(packageMapping) { segment, isLast ->
                // A package is never at the end of a route, so no segment of its mapping can be a catch-all or
                // optional. Only the last can be inferred, as otherwise several would be named after the package.
                segment.isCatchAll || segment.isOptional || (segment.isInferred && !isLast)
            }
        }

        /**
         * Returns the characters (each a single code point) in [segment] that can't be part of a URL path as written,
         * in the order they first appear, e.g. " " for "about us".
         *
         * These are the characters that the browser percent-encodes (see
         * [the path percent-encode set](https://url.spec.whatwg.org/#path-percent-encode-set)), including whitespace
         * and all non-ASCII characters, plus "?" and "#", which start the query and fragment, and "\", which browsers
         * treat as "/" (for http and https URLs). Kobweb never decodes the paths it matches routes against, so a route
         * with any of these can never match.
         *
         * Curly braces aren't included, as they're part of dynamic segments (and reported separately elsewhere).
         */
        fun findInvalidCharacters(segment: String): List<String> {
            return segment.codePoints().toArray()
                .map { codePoint -> String(Character.toChars(codePoint)) }
                .filter { char -> char != "{" && char != "}" && !char.isValidInUrlPath() }
                .distinct()
        }

        private fun Char.isValidInUrlPath() = this in '!'..'~' && this !in "\"#<>?`{}\\"
        private fun String.isValidInUrlPath() = length == 1 && this[0].isValidInUrlPath()

        private fun findIn(
            value: String,
            isMisplaced: (segment: DynamicRouteSegment, isLast: Boolean) -> Boolean,
        ): List<RouteValueProblem> {
            // A blank route override just means no override, and a blank package mapping is a problem with the whole
            // value rather than any segment of it (which the caller reports)
            if (value.isBlank()) return emptyList()

            val problems = mutableListOf<RouteValueProblem>()
            val segments = value.split('/')
            var start = 0
            segments.forEachIndexed { i, segment ->
                val range = TextRange.from(start, segment.length)
                val isLast = i == segments.lastIndex
                val dynamicSegment = DynamicRouteSegment.tryCreate(segment)
                when {
                    segment.isEmpty() -> {
                        // Empty first and last segments just mean the value starts or ends with a slash
                        if (i != 0 && !isLast) {
                            // Highlight the slashes around the empty segment
                            problems.add(RouteValueProblem(Kind.EMPTY_SEGMENT, TextRange(start - 1, start + 1)))
                        }
                    }

                    dynamicSegment != null -> {
                        // The name has had any "..." and "?" removed already, so this also catches mistakes like
                        // "{rest...}" and "{.....rest}". (Names never appear in the URL, so they aren't limited to
                        // ASCII.)
                        if (!dynamicSegment.name.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                            problems.add(RouteValueProblem(Kind.MALFORMED_DYNAMIC_SEGMENT, range))
                        } else if (isMisplaced(dynamicSegment, isLast)) {
                            problems.add(RouteValueProblem(Kind.MISPLACED_DYNAMIC_SEGMENT, range))
                        }
                    }

                    segment.replace("%2e", ".", ignoreCase = true) in listOf(".", "..") ->
                        problems.add(RouteValueProblem(Kind.DOT_SEGMENT, range))

                    else -> {
                        // Stray braces and other invalid characters are separate mistakes, so both are reported
                        if (segment.contains('{') || segment.contains('}')) {
                            problems.add(RouteValueProblem(Kind.STRAY_CURLY_BRACES, range))
                        }
                        if (findInvalidCharacters(segment).isNotEmpty()) {
                            problems.add(RouteValueProblem(Kind.INVALID_CHARACTER, range))
                        }
                    }
                }
                start += segment.length + 1
            }
            return problems
        }
    }
}
