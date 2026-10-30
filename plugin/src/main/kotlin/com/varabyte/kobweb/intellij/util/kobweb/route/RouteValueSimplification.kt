package com.varabyte.kobweb.intellij.util.kobweb.route

import org.jetbrains.kotlin.name.FqName

/**
 * A simpler way to write a value that Kobweb builds routes from (i.e. a route override, e.g. in `@Page` or `@Api`, or a
 * `@file:PackageMapping`), which has the same effect.
 *
 * For example, in `pages/blog/Post.kt`, `@Page("post")` can be removed, as the route is "/blog/post" either way (see
 * [Redundant]), and `@Page("{post}")` can be shortened to `@Page("{}")`, which Kobweb names after the file (see
 * [Shorten]).
 */
sealed interface RouteValueSimplification {
    /** The value can be removed, as Kobweb would generate the same route without it. */
    data object Redundant : RouteValueSimplification

    /** The value can be replaced with [simplerValue], e.g. "{post}" with "{}". */
    data class Shorten(val simplerValue: String) : RouteValueSimplification

    companion object {
        /**
         * Returns how a declaration's route override could be simplified, or null if it can't be.
         *
         * @param fileName The name of the file the declaration lives in, without its extension.
         * @param resolve Returns the route Kobweb would register the declaration at with the given route override (or
         *   with none, if null), or null if Kobweb would reject it.
         */
        fun forRouteOverride(
            fileName: String,
            routeOverride: String,
            resolve: (routeOverride: String?) -> Route?,
        ): RouteValueSimplification? {
            // Kobweb treats blank overrides the same as no override
            if (routeOverride.isBlank()) return Redundant

            // If Kobweb rejects the declaration, there's no route to compare against
            val route = resolve(routeOverride) ?: return null
            if (resolve(null) == route) return Redundant

            // A dynamic segment named after the file can be inferred, e.g. "{post}" in "Post.kt" can be "{}"
            val lastSegment = DynamicRouteSegment.tryCreate(routeOverride.substringAfterLast('/'))
                ?.takeIf { !it.isInferred && it.name == RouteResolver.slugFor(fileName) }
                ?: return null
            val inferredSegment = lastSegment.withReplacedName("").rawValue
            return listOfNotNull(
                // e.g. "/blog/{post}" under package "pages.blog" can be just "{}"
                inferredSegment,
                // Otherwise, keep the rest of the override, e.g. "/other/{post}" can be "/other/{}"
                routeOverride.takeIf { it.contains('/') }?.let { it.substringBeforeLast('/') + "/" + inferredSegment },
            )
                .firstOrNull { candidate -> resolve(candidate) == route }
                ?.let(::Shorten)
        }

        /**
         * Returns how a package mapping for [pkg] could be simplified, or null if it can't be.
         */
        fun forPackageMapping(pkg: FqName, packageMapping: String): RouteValueSimplification? {
            val packageSegment = RouteResolver.segmentFor(pkg)
            if (packageMapping == packageSegment) return Redundant

            // A dynamic segment named after the package can be inferred, e.g. "{user}" for "pages.user" can be "{}"
            val dynamicSegment = DynamicRouteSegment.tryCreate(packageMapping) ?: return null
            // (Catch-all and optional segments aren't allowed in package mappings, which is reported separately)
            if (dynamicSegment.isCatchAll || dynamicSegment.isOptional) return null
            return Shorten("{}").takeIf { dynamicSegment.name == packageSegment }
        }
    }
}
