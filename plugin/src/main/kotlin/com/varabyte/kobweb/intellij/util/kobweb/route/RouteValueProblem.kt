package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.openapi.util.TextRange

/**
 * A problem with a value that Kobweb builds routes from, i.e. a `@Page` route override or a `@file:PackageMapping`.
 *
 * @property range Where the problem is, within the value.
 */
class RouteValueProblem(val kind: Kind, val range: TextRange) {
    enum class Kind {
        /**
         * An inferred (e.g. "{}"), catch-all (e.g. "{...rest}"), or optional (e.g. "{id?}") dynamic segment somewhere
         * other than the end of a route, which Kobweb doesn't allow.
         *
         * For a route override, Kobweb skips the page. Package mappings are never at the end of a route, so they can
         * never use catch-all or optional segments (although inferred ones are fine).
         */
        MISPLACED_DYNAMIC_SEGMENT,

        /**
         * A segment with curly braces that isn't a valid dynamic segment, e.g. "{post" or "a{b}c", which Kobweb currently
         * treats as a plain segment.
         */
        MALFORMED_DYNAMIC_SEGMENT,

        /** An empty segment, e.g. in "a//b". */
        EMPTY_SEGMENT,

        /** A segment with a character that can't be part of a URL path, i.e. "?" or "#". */
        INVALID_CHARACTER,
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
         */
        fun findInPackageMapping(packageMapping: String): List<RouteValueProblem> {
            return findIn(packageMapping) { segment, _ -> segment.isCatchAll || segment.isOptional }
        }

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
                when {
                    segment.isEmpty() -> {
                        // Empty first and last segments just mean the value starts or ends with a slash
                        if (i != 0 && !isLast) {
                            // Highlight the slashes around the empty segment
                            problems.add(RouteValueProblem(Kind.EMPTY_SEGMENT, TextRange(start - 1, start + 1)))
                        }
                    }

                    segment.contains('{') || segment.contains('}') -> {
                        val dynamicSegment = DynamicRouteSegment.tryCreate(segment)
                            // Kobweb accepts values like "{a}{b}" as dynamic segments, but they're surely mistakes
                            ?.takeIf { '{' !in it.name && '}' !in it.name }
                        when {
                            dynamicSegment == null -> problems.add(RouteValueProblem(Kind.MALFORMED_DYNAMIC_SEGMENT, range))
                            isMisplaced(dynamicSegment, isLast) ->
                                problems.add(RouteValueProblem(Kind.MISPLACED_DYNAMIC_SEGMENT, range))
                        }
                    }

                    segment.contains('?') || segment.contains('#') ->
                        problems.add(RouteValueProblem(Kind.INVALID_CHARACTER, range))
                }
                start += segment.length + 1
            }
            return problems
        }
    }
}
