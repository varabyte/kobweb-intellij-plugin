package com.varabyte.kobweb.intellij.util.kobweb.route

import org.jetbrains.kotlin.name.FqName

/**
 * Works out the routes Kobweb generates for pages, mirroring Kobweb's KSP processor (see `toPageEntry` in Kobweb's
 * `site-processors` module).
 *
 * For example, a page in file `Post.kt`, under package `com.example.pages.blog`, gets the route "/blog/post", which its
 * `@Page` annotation can override (see the `Page` docs in Kobweb for all the ways it can do so).
 *
 * This only deals with names, not PSI, so it can be used (and tested) without a project.
 *
 * @param pagesPackage The fully qualified root package of all pages, e.g. "com.example.pages".
 * @param packageMappings See [RouteResolver].
 */
class PageRouteResolver(val pagesPackage: FqName, packageMappings: Map<FqName, String>) {
    private val routeResolver = RouteResolver(pagesPackage, packageMappings)

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
        val route = routeResolver.resolve(pagePackage, fileName, routeOverride) ?: return null
        // "index" pages are served at their parent route
        return PageRoute(if (route.endsWith("/index")) route.removeSuffix("index") else route)
    }
}
