package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.varabyte.kobweb.intellij.services.conf.KobwebConfReader
import com.varabyte.kobweb.intellij.util.kobweb.conf.findKobwebConf
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * A redirect from a site's `.kobweb/conf.yaml`, which sends visitors of any path that matches [from] (a regex) to [to]
 * instead, e.g. from "/socials/facebook/([^/]+)" to "/socials/meta/$1".
 *
 * Kobweb applies these to paths before looking for the page they lead to, so they win over pages, too.
 *
 * @property to Where matching paths are sent, which may refer to groups captured by [from] (e.g. "$1").
 * @property source The element this redirect was read from, e.g. its entry in `.kobweb/conf.yaml`, if any.
 */
class RouteRedirect(val from: String, val to: String, val source: PsiElement? = null) {
    // Kobweb matches the whole path. Invalid patterns can't match anything (and Kobweb would fail to start anyway)
    private val pattern: Pattern? = try {
        Pattern.compile("^$from$")
    } catch (_: PatternSyntaxException) {
        null
    }

    /**
     * Returns where this redirect sends [path], or null if it doesn't match, e.g. "/socials/meta/bob" for
     * "/socials/facebook/bob".
     */
    fun map(path: String): String? {
        val matcher = pattern?.matcher(path)?.takeIf { it.matches() } ?: return null
        var redirected = to
        // In reverse, so that "$1" doesn't replace the start of "$12"
        for (i in matcher.groupCount() downTo 0) {
            redirected = redirected.replace("\$$i", matcher.group(i).orEmpty())
        }
        return redirected
    }

    /**
     * Returns true if this redirect could match any path that starts with [prefix], which is useful for paths that
     * aren't fully known until runtime, e.g. "/posts/" for "/posts/$id".
     *
     * Returning false means that no such path is ever redirected, but true just means that some might be, e.g. for
     * the redirect "/legacy/(.+)", "/leg" returns true (as "/legacy/a" starts with it) and "/blog/" returns false.
     */
    fun mightMatchPathStartingWith(prefix: String): Boolean {
        val matcher = pattern?.matcher(prefix) ?: return false
        // If matching gave up before reaching the end of the prefix, no amount of text after it can make it match
        return matcher.matches() || matcher.hitEnd()
    }
}

/**
 * Returns the redirects of the Kobweb site this element belongs to (see [findKobwebConf]), in the order they're listed,
 * which is the order Kobweb applies them in.
 *
 * NOTE: This will always empty if IntelliJ's YAML plugin is disabled, as the file can't be read without it (see
 * [KobwebConfReader]).
 */
fun PsiElement.findRouteRedirects(): List<RouteRedirect> {
    val reader = KobwebConfReader.getInstance() ?: return emptyList()
    val conf = findKobwebConf() ?: return emptyList()
    return CachedValuesManager.getCachedValue(conf) {
        CachedValueProvider.Result.create(reader.readRedirects(conf), conf)
    }
}

/**
 * Returns where [redirects] send this path, or this same path if none of them apply, e.g. "/new-page" for "/old-page".
 *
 * Like Kobweb, each redirect applies to the result of the ones before it, and the final result is always treated as
 * rooted, e.g. "new-page" is "/new-page".
 *
 * Paths with segments that aren't known until runtime (e.g. "/posts/$id") can't be checked against redirects, so they're
 * always returned as is (see also [mightBeRedirectedBy]).
 */
fun LinkPath.redirectedBy(redirects: List<RouteRedirect>): LinkPath {
    if (redirects.isEmpty() || hasUnknownSegments) return this
    val path = toString()
    val redirected = redirects.fold(path) { current, redirect -> redirect.map(current) ?: current }
        .let { if (it.startsWith('/')) it else "/$it" }
    return if (redirected != path) LinkPath.parse(redirected) ?: this else this
}

/**
 * Returns true if any of [redirects] might apply to this path, including paths with segments that aren't known until
 * runtime, which only have the part before their first unknown segment checked (e.g. "/posts/" for "/posts/$id").
 */
fun LinkPath.mightBeRedirectedBy(redirects: List<RouteRedirect>): Boolean {
    if (!hasUnknownSegments) return redirectedBy(redirects) !== this
    val knownStart = segments.takeWhile { segment -> segment.text != null }.joinToString("") { "${it.text}/" }
    return redirects.any { redirect -> redirect.mightMatchPathStartingWith("/$knownStart") }
}
