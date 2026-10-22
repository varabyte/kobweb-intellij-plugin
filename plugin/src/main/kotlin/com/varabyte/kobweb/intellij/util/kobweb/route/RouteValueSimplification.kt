package com.varabyte.kobweb.intellij.util.kobweb.route

import org.jetbrains.kotlin.name.FqName

/**
 * A simpler way to write a value that Kobweb builds routes from (i.e. a `@Page` route override or a
 * `@file:PackageMapping`), which has the same effect.
 */
sealed interface RouteValueSimplification {
    /** The value can be removed, as Kobweb would generate the same route without it. */
    data object Redundant : RouteValueSimplification

    /** The value can be replaced with [simplerValue], e.g. "{post}" with "{}". */
    data class Shorten(val simplerValue: String) : RouteValueSimplification

    companion object {
        /**
         * Returns how a page's route override could be simplified, or null if it can't be.
         *
         * @param resolver The resolver for the page's routes.
         * @param pagePackage The package the page lives in.
         * @param fileName The name of the file the page lives in, without its extension.
         */
        fun forRouteOverride(
            resolver: PageRouteResolver,
            pagePackage: FqName,
            fileName: String,
            routeOverride: String,
        ): RouteValueSimplification? {
            // Kobweb treats blank overrides the same as no override
            if (routeOverride.isBlank()) return Redundant

            // If Kobweb skips the page, there's no route to compare against
            val route = resolver.resolve(pagePackage, fileName, routeOverride) ?: return null
            if (resolver.resolve(pagePackage, fileName, null) == route) return Redundant

            // A dynamic segment named after the file can be inferred, e.g. "{post}" in "Post.kt" can be "{}"
            val lastSegment = DynamicRouteSegment.tryCreate(routeOverride.substringAfterLast('/'))
                ?.takeIf { !it.isInferred && it.name == PageRouteResolver.slugFor(fileName) }
                ?: return null
            val inferredSegment = lastSegment.withReplacedName("").rawValue
            return listOfNotNull(
                // e.g. "/blog/{post}" under package "pages.blog" can be just "{}"
                inferredSegment,
                // Otherwise, keep the rest of the override, e.g. "/other/{post}" can be "/other/{}"
                routeOverride.takeIf { it.contains('/') }?.let { it.substringBeforeLast('/') + "/" + inferredSegment },
            )
                .firstOrNull { candidate -> resolver.resolve(pagePackage, fileName, candidate) == route }
                ?.let(::Shorten)
        }

        /**
         * Returns how a package mapping for [pkg] could be simplified, or null if it can't be.
         *
         * Note that, unlike a blank route override, a blank package mapping, if allowed, would be destructive to the
         * final route. That's why it's reported as an error (see `InvalidRouteInspection`) instead of as redundant.
         */
        fun forPackageMapping(pkg: FqName, packageMapping: String): RouteValueSimplification? {
            val packageSegment = PageRouteResolver.segmentFor(pkg)
            if (packageMapping == packageSegment) return Redundant

            // A dynamic segment named after the package can be inferred, e.g. "{user}" for "pages.user" can be "{}"
            val dynamicSegment = DynamicRouteSegment.tryCreate(packageMapping) ?: return null
            // (Catch-all and optional segments aren't allowed in package mappings, which is reported separately)
            if (dynamicSegment.isCatchAll || dynamicSegment.isOptional) return null
            return Shorten("{}").takeIf { dynamicSegment.name == packageSegment }
        }
    }
}
