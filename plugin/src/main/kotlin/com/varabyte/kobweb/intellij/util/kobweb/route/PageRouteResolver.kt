package com.varabyte.kobweb.intellij.util.kobweb.route

import com.varabyte.kobweb.intellij.util.text.camelCaseToKebabCase
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isSubpackageOf

/**
 * Works out the routes Kobweb generates for pages, mirroring Kobweb's KSP processor (see `processPagesFun` and
 * `processRoute` in Kobweb's `site-processors` module).
 *
 * For example, a page in file `Post.kt`, under package `com.example.pages.blog`, gets the route "/blog/post", which its
 * `@Page` annotation can override (see the `Page` docs in Kobweb for all the ways it can do so).
 *
 * This only deals with names, not PSI, so it can be used (and tested) without a project.
 *
 * @param pagesPackage The fully qualified root package of all pages, e.g. "com.example.pages".
 * @param packageMappings Route segments that replace the names of packages under [pagesPackage], set by
 *   `@file:PackageMapping` annotations, e.g. "com.example.pages.blog" to "articles". These are the raw values as
 *   written in the annotation, so an inferred dynamic segment ("{}") at the end of one will be named after its package.
 */
class PageRouteResolver(val pagesPackage: FqName, private val packageMappings: Map<FqName, String>) {
    /**
     * Returns the route that Kobweb generates for a page, e.g. "/blog/post" or "/users/{user}/posts/{post}".
     *
     * @param pagePackage The package the page lives in.
     * @param fileName The name of the file the page lives in, without its extension, e.g. "Post".
     * @param routeOverride The value passed into the page's `@Page` annotation, if any.
     *
     * @return null if Kobweb rejects this page.
     */
    fun resolve(pagePackage: FqName, fileName: String, routeOverride: String?): PageRoute? {
        @Suppress("NAME_SHADOWING") // Kobweb treats a blank override the same as no override
        val routeOverride = routeOverride?.takeIf { it.isNotBlank() }
        val isAbsolute = routeOverride?.startsWith('/') == true

        // Only whole package names count, so pages in "pages_old" aren't under "pages"
        if (!isAbsolute && !pagePackage.isSubpackageOf(pagesPackage)) return null

        // Only the last segment can be inferred, a catch-all, or optional, e.g. "/a/{b}/c/{}" is OK but "/a/{}/c/{b}"
        // isn't. (Kobweb only checks the override here, so package mappings get no such check.)
        if (routeOverride != null && routeOverride.substringBeforeLast('/', missingDelimiterValue = "")
                .split('/')
                .mapNotNull { DynamicRouteSegment.tryCreate(it) }
                .any { it.isInferred || it.isCatchAll || it.isOptional }
        ) return null

        val fileSlug = slugFor(fileName)
        val routeWithoutSlug = if (isAbsolute) {
            // An absolute override replaces the whole route, e.g. "/a/b/c"
            routeOverride.substringBeforeLast('/') + '/'
        } else {
            // A relative override with subdirectories extends the page's package, e.g. "a/b/c"
            val routeExtra = routeOverride?.takeIf { it.contains('/') }?.let { it.substringBeforeLast('/') + '/' }
            routeFor(pagePackage) + routeExtra.orEmpty()
        }

        val slug = if (routeOverride != null && !routeOverride.endsWith('/')) {
            val value = routeOverride.substringAfterLast('/')
            DynamicRouteSegment.tryCreate(value)?.takeIf { it.isInferred }?.withReplacedName(fileSlug)?.rawValue
                ?: value
        } else {
            // An override that ends with a slash only changes the route that the page lives under, e.g. "a/b/c/"
            fileSlug
        }

        // "index" pages are served at their parent route
        return PageRoute("$routeWithoutSlug$slug".let { if (it.endsWith("/index")) it.removeSuffix("index") else it })
    }

    /**
     * Returns the route for a package under [pagesPackage], which always begins and ends with a slash, e.g. "/blog/"
     * for "com.example.pages.blog" (or "/" for the pages package itself).
     */
    private fun routeFor(pkg: FqName): String {
        val segments = mutableListOf<String>()
        var curr = pkg
        while (curr != pagesPackage && !curr.isRoot) {
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
         * Returns the route segment Kobweb generates for a page's file name (without its extension), e.g.
         * "blog-post" for "BlogPost".
         */
        fun slugFor(fileName: String): String = segmentFor(fileName)

        /**
         * Returns the route segment Kobweb generates for a package (unless a package mapping says otherwise), e.g.
         * "our-team" for "com.example.pages.ourTeam".
         */
        fun segmentFor(pkg: FqName): String = segmentFor(pkg.shortName().asString())

        // Converts a name to kebab-case, also collapsing underscores (which are usually there to work around
        // reserved words or names that start with a digit), e.g. "_2021" -> "2021" and "fun_" -> "fun"
        private fun segmentFor(name: String): String {
            return name
                .camelCaseToKebabCase()
                .trim('_')
                .replace(Regex("_+"), "-")
                .replace(Regex("-+"), "-")
        }
    }
}
