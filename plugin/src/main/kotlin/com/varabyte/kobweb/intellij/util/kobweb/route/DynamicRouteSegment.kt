package com.varabyte.kobweb.intellij.util.kobweb.route

// Mirrors `DynamicRouteSegment` in Kobweb's `processor-common` module

/**
 * A dynamic segment in a route, e.g. "{example}" in "/a/{example}/z".
 *
 * Besides plain named segments, Kobweb supports catch-all segments (e.g. "{...rest}"), which match the rest of a URL,
 * optional segments (e.g. "{example?}"), which also match an empty segment, and inferred segments (e.g. "{}"), whose
 * name comes from the page's file (or package, for a package mapping).
 */
class DynamicRouteSegment private constructor(val rawValue: String) {
    private val content = rawValue.removeSurrounding("{", "}")

    val isCatchAll get() = content.startsWith("...")
    val isOptional get() = content.endsWith("?")

    val name = content.removePrefix("...").removeSuffix("?")

    val isInferred get() = name.isEmpty()

    /**
     * Convert this dynamic route segment into one with a new name, e.g. an inferred "{}" into a named "{example}".
     */
    fun withReplacedName(newName: String): DynamicRouteSegment {
        return DynamicRouteSegment(buildString {
            append('{')
            if (isCatchAll) append("...")
            append(newName)
            if (isOptional) append('?')
            append('}')
        })
    }

    companion object {
        /**
         * Returns the dynamic route segment for [rawValue], or null if it isn't one (i.e. it's a static segment).
         *
         * Like Kobweb, this only checks that the value is surrounded by curly braces, so values like "{a}{b}" count.
         */
        fun tryCreate(rawValue: String): DynamicRouteSegment? {
            return if (rawValue.startsWith('{') && rawValue.endsWith('}')) DynamicRouteSegment(rawValue) else null
        }
    }
}
