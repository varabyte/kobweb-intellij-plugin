@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.util.kobweb.route

import com.varabyte.truthish.assertThat
import org.junit.Test

class RouteRedirectsTest {
    private fun redirect(redirects: List<RouteRedirect>, path: String) =
        LinkPath.parse(path)!!.redirectedBy(redirects).toString()

    @Test
    fun redirectsMatchWholePaths() {
        val redirect = RouteRedirect("/old-page", "/new-page")

        assertThat(redirect.map("/old-page")).isEqualTo("/new-page")
        assertThat(redirect.map("/old-page/extra")).isNull()
        assertThat(redirect.map("/prefix/old-page")).isNull()
    }

    @Test
    fun redirectsCanUseTheirOwnAnchorsAndAlternatives() {
        // Redundant anchors are harmless, as "^^" and "$$" match the same as "^" and "$"
        assertThat(RouteRedirect("^/old-page$", "/new-page").map("/old-page")).isEqualTo("/new-page")

        // Each alternative still has to match the whole path
        val redirect = RouteRedirect("/a|/b", "/c")
        assertThat(redirect.map("/a")).isEqualTo("/c")
        assertThat(redirect.map("/b")).isEqualTo("/c")
        assertThat(redirect.map("/a/b")).isNull()
    }

    @Test
    fun redirectsCanUseCapturedGroups() {
        assertThat(RouteRedirect("/socials/facebook/([^/]+)", "/socials/meta/$1").map("/socials/facebook/bob"))
            .isEqualTo("/socials/meta/bob")
        assertThat(RouteRedirect("/(a)/(b)", "/$2/$1/$0").map("/a/b")).isEqualTo("/b/a//a/b")

        // "$12" is group 12, not group 1 followed by "2"
        val twelveGroups = (1..12).joinToString("/", prefix = "/") { "(g$it)" }
        val twelveSegments = (1..12).joinToString("/", prefix = "/") { "g$it" }
        assertThat(RouteRedirect(twelveGroups, "/$12").map(twelveSegments)).isEqualTo("/g12")
    }

    @Test
    fun invalidPatternsDontMatchAnything() {
        val redirect = RouteRedirect("/old-(page", "/new-page")

        assertThat(redirect.map("/old-(page")).isNull()
        assertThat(redirect.mightMatchPathStartingWith("/")).isFalse()
    }

    @Test
    fun redirectsApplyInOrderToEachOthersResults() {
        // The chaining example from Kobweb's docs
        val redirects = listOf(
            RouteRedirect("/socials/facebook/([^/]+)", "/socials/meta/$1"),
            RouteRedirect("(/socials/meta)/about-facebook", "$1/about-meta"),
        )

        assertThat(redirect(redirects, "/socials/facebook/about-facebook")).isEqualTo("/socials/meta/about-meta")
        assertThat(redirect(redirects, "/socials/facebook/bob")).isEqualTo("/socials/meta/bob")
        assertThat(redirect(redirects, "/blog/post")).isEqualTo("/blog/post")
    }

    @Test
    fun redirectedPathsAreRooted() {
        assertThat(redirect(listOf(RouteRedirect("/old-page", "new-page")), "/old-page")).isEqualTo("/new-page")
        // Only the final result is rooted, so later redirects see what earlier ones returned
        val redirects = listOf(
            RouteRedirect("/old-page", "new-page"),
            RouteRedirect("new-page", "/newer-page"),
        )
        assertThat(redirect(redirects, "/old-page")).isEqualTo("/newer-page")
    }

    @Test
    fun pathsThatArentRedirectedAreReturnedAsIs() {
        val path = LinkPath.parse("/blog/post")!!
        assertThat(path.redirectedBy(listOf(RouteRedirect("/old-page", "/new-page")))).isSameAs(path)
    }

    @Test
    fun redirectsMightMatchPathsThatStartTheSameWay() {
        val redirect = RouteRedirect("/legacy/(.+)", "/blog/$1")

        assertThat(redirect.mightMatchPathStartingWith("/legacy/")).isTrue()
        assertThat(redirect.mightMatchPathStartingWith("/leg")).isTrue()
        assertThat(redirect.mightMatchPathStartingWith("/blog/")).isFalse()
    }
}
