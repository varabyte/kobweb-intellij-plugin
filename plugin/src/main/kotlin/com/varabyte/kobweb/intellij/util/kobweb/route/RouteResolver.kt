package com.varabyte.kobweb.intellij.util.kobweb.route

import com.varabyte.kobweb.intellij.util.text.camelCaseToKebabCase
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isSubpackageOf

/**
 * Works out the routes Kobweb generates for declarations from the file they live in, e.g. pages and API endpoints,
 * mirroring `KSFile.toRoute` in Kobweb's `site-processors` module.
 *
 * For example, a declaration in file `Post.kt`, under package `com.example.pages.blog` (where `com.example.pages` is
 * the root package), gets the route "/blog/post", which a route override can change (see the `Page` docs in Kobweb for
 * all the ways it can do so).
 *
 * Any rules specific to one kind of declaration (e.g. "index" pages) are left to the caller (see [PageRouteResolver]).
 *
 * This only deals with names, not PSI, so it can be used (and tested) without a project.
 *
 * @param rootPackage The fully qualified package that routes are relative to, e.g. "com.example.pages".
 * @param packageMappings Route segments that replace the names of packages under [rootPackage], set by
 *   `@file:PackageMapping` annotations, e.g. "com.example.pages.blog" to "articles". These are the raw values as
 *   written in the annotation, so an inferred dynamic segment ("{}") at the end of one will be named after its package.
 */
class RouteResolver(val rootPackage: FqName, private val packageMappings: Map<FqName, String>) {
    /**
     * Returns the route that Kobweb generates for a declaration, e.g. "/blog/post" or "/users/{user}/posts/{post}".
     *
     * @param pkg The package the declaration lives in.
     * @param fileName The name of the file the declaration lives in, without its extension, e.g. "Post".
     * @param routeOverride The route override for the declaration (e.g. the value passed into its `@Page` annotation),
     *   if any. A blank one counts as none, as it does in Kobweb.
     *
     * @return null if Kobweb rejects this declaration.
     */
    fun resolve(pkg: FqName, fileName: String, routeOverride: String?): String? {
        @Suppress("NAME_SHADOWING")
        val routeOverride = routeOverride?.takeIf { it.isNotBlank() }
        val isAbsolute = routeOverride?.startsWith('/') == true

        // Only whole package names count, so "com.example.pages_old" isn't under "com.example.pages"
        if (!isAbsolute && !pkg.isSubpackageOf(rootPackage)) return null

        // Only the last segment can be inferred, a catch-all, or optional, e.g. "/a/{b}/c/{}" is OK but "/a/{}/c/{b}"
        // isn't. (Kobweb only checks the override here, so package mappings get no such check.)
        if (routeOverride != null && routeOverride.substringBeforeLast('/', missingDelimiterValue = "")
                .split('/')
                .mapNotNull { DynamicRouteSegment.tryCreate(it) }
                .any { it.isInferred || it.isCatchAll || it.isOptional }
        ) return null

        val fileSlug = slugFor(fileName)
        val routeWithoutSlug = if (routeOverride?.startsWith('/') == true) {
            // An absolute override replaces the whole route, e.g. "/a/b/c"
            routeOverride.substringBeforeLast('/') + '/'
        } else {
            // A relative override with subdirectories extends the declaration's package, e.g. "a/b/c"
            val routeExtra = routeOverride?.takeIf { it.contains('/') }?.let { it.substringBeforeLast('/') + '/' }
            routeFor(pkg) + routeExtra.orEmpty()
        }

        val slug = if (routeOverride != null && !routeOverride.endsWith('/')) {
            val value = routeOverride.substringAfterLast('/')
            DynamicRouteSegment.tryCreate(value)?.takeIf { it.isInferred }?.withReplacedName(fileSlug)?.rawValue
                ?: value
        } else {
            // An override that ends with a slash only changes the route that the declaration lives under, e.g.
            // "a/b/c/"
            fileSlug
        }

        return "$routeWithoutSlug$slug"
    }

    /**
     * Returns the route for a package, which always begins and ends with a slash, e.g. "/blog/" for
     * "com.example.pages.blog" (or "/" for [rootPackage] itself).
     */
    private fun routeFor(pkg: FqName): String {
        val segments = mutableListOf<String>()
        var curr = pkg
        while (curr != rootPackage && !curr.isRoot) {
            val mapping = packageMappings[curr]?.let { mapping ->
                // e.g. "{}" under "pages.users" is "{users}", or "a/{}" is "a/{users}". (Only the last segment of a
                // mapping can be inferred.)
                val mappingSegments = mapping.split('/')
                val lastSegment = mappingSegments.last()
                val inferredLastSegment = DynamicRouteSegment.tryCreate(lastSegment)?.takeIf { it.isInferred }
                    ?.withReplacedName(segmentFor(curr))?.rawValue
                    ?: lastSegment
                (mappingSegments.dropLast(1) + inferredLastSegment).joinToString("/")
            }
            segments.add(0, mapping ?: segmentFor(curr))
            curr = curr.parent()
        }
        return (segments.joinToString("/") + "/").let { if (it.startsWith('/')) it else "/$it" }
    }

    companion object {
        /**
         * Returns the route segment Kobweb generates for a file name (without its extension), e.g. "blog-post" for
         * "BlogPost".
         */
        fun slugFor(fileName: String): String = segmentFor(fileName)

        /**
         * Returns the route segment Kobweb generates for a package (unless a package mapping says otherwise), e.g.
         * "our-team" for "com.example.pages.ourTeam".
         */
        fun segmentFor(pkg: FqName): String = segmentFor(pkg.shortName().asString())

        // Converts a name to kebab-case, collapsing runs of underscores and hyphens (underscores are usually there to
        // work around reserved words or names that start with a digit) and trimming them from the ends, e.g.
        // "_2021" -> "2021" and "fun_" -> "fun". (See `toRouteSegment` in Kobweb.)
        private fun segmentFor(name: String): String {
            return name.camelCaseToKebabCase().replace(Regex("[_-]+"), "-").trim('-')
        }
    }
}
