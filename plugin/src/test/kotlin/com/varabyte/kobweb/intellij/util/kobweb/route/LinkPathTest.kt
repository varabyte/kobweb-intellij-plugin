package com.varabyte.kobweb.intellij.util.kobweb.route

import com.varabyte.truthish.assertThat
import org.junit.Test

class LinkPathTest {
    /**
     * @param path A path written as text, where "$" marks a template expression, e.g. "/posts/$" for "/posts/$id"
     */
    private fun path(path: String): LinkPath? {
        val parts = path.split('$').flatMapIndexed { i, part -> if (i == 0) listOf(part) else listOf(null, part) }
        return LinkPath.parse(parts)
    }

    private fun leadsTo(path: String, route: String) = path(path)!!.leadsTo(route)

    @Test
    fun onlyAbsolutePathsWithinTheSiteAreParsed() {
        assertThat(path("/blog/post")?.segments).isEqualTo(listOf("blog", "post"))
        assertThat(path("/")?.segments).isEqualTo(listOf(""))
        assertThat(path("/blog/")?.segments).isEqualTo(listOf("blog", ""))

        assertThat(path("blog/post")).isNull()
        assertThat(path("https://example.com/blog")).isNull()
        assertThat(path("//example.com/blog")).isNull()
        assertThat(path("mailto:me@example.com")).isNull()
        assertThat(path("#comments")).isNull()
        assertThat(path("")).isNull()
        // We can't tell what a template expression at the start holds
        assertThat(path("$/post")).isNull()
    }

    @Test
    fun queryParametersAndFragmentsAreIgnored() {
        assertThat(path("/blog/post?sort=new")?.segments).isEqualTo(listOf("blog", "post"))
        assertThat(path("/blog/post#comments")?.segments).isEqualTo(listOf("blog", "post"))
        assertThat(path("/blog/?page=2")?.segments).isEqualTo(listOf("blog", ""))
        // Even if the query has a template expression
        assertThat(path("/blog/post?id=$")?.segments).isEqualTo(listOf("blog", "post"))
    }

    @Test
    fun segmentsWithTemplateExpressionsAreUnknown() {
        assertThat(path("/posts/$")?.segments).isEqualTo(listOf("posts", null))
        assertThat(path("/posts/post-$/edit")?.segments).isEqualTo(listOf("posts", null, "edit"))
    }

    @Test
    fun staticRoutesMatchTheSameText() {
        assertThat(leadsTo("/blog/post", "/blog/post")).isTrue()
        assertThat(leadsTo("/", "/")).isTrue()
        assertThat(leadsTo("/blog/post", "/blog/other")).isFalse()
        assertThat(leadsTo("/blog", "/blog/post")).isFalse()
        assertThat(leadsTo("/blog/post/extra", "/blog/post")).isFalse()
    }

    @Test
    fun trailingSlashesAreAddedOrRemovedToMakeAMatchIfNecessary() {
        assertThat(leadsTo("/blog", "/blog/")).isTrue()
        assertThat(leadsTo("/blog/", "/blog")).isTrue()
        // Except for the root, which the router leaves alone
        assertThat(leadsTo("/", "/blog")).isFalse()
    }

    @Test
    fun dynamicSegmentsMatchAnyNonEmptySegment() {
        assertThat(leadsTo("/users/bob/posts", "/users/{user}/posts")).isTrue()
        assertThat(leadsTo("/users//posts", "/users/{user}/posts")).isFalse()
        assertThat(leadsTo("/users/bob", "/users/{user}/posts")).isFalse()
    }

    @Test
    fun optionalSegmentsAlsoMatchMissingOrEmptySegments() {
        assertThat(leadsTo("/users/bob", "/users/{user?}")).isTrue()
        assertThat(leadsTo("/users/", "/users/{user?}")).isTrue()
        assertThat(leadsTo("/users", "/users/{user?}")).isTrue()
        assertThat(leadsTo("/users/bob/posts", "/users/{user?}")).isFalse()
    }

    @Test
    fun catchAllSegmentsMatchTheRestOfThePath() {
        assertThat(leadsTo("/docs/a", "/docs/{...path}")).isTrue()
        assertThat(leadsTo("/docs/a/b/c", "/docs/{...path}")).isTrue()
        assertThat(leadsTo("/docs", "/docs/{...path}")).isFalse()
        assertThat(leadsTo("/docs/", "/docs/{...path}")).isFalse()

        assertThat(leadsTo("/docs", "/docs/{...path?}")).isTrue()
        assertThat(leadsTo("/docs/a/b", "/docs/{...path?}")).isTrue()
    }

    @Test
    fun unknownSegmentsOnlyMatchDynamicSegments() {
        assertThat(leadsTo("/posts/$", "/posts/{post}")).isTrue()
        assertThat(leadsTo("/posts/$", "/posts/new")).isFalse()
        assertThat(leadsTo("/posts/$/edit", "/posts/{post}/edit")).isTrue()
        assertThat(leadsTo("/docs/$", "/docs/{...path}")).isTrue()
    }

    @Test
    fun routerPrecedenceIsUsedForConcretePaths() {
        val pages = listOf("/posts/new", "/posts/{post}", "/blog", "/blog/", "/docs/{...path}", "/docs/intro")
            .map { route -> PageDefinition(fqn = route, route = route, function = null) }
        fun routeLedToBy(path: String) = pages.findLedToBy(path(path)!!)?.route

        // Static segments win over dynamic ones
        assertThat(routeLedToBy("/posts/new")).isEqualTo("/posts/new")
        assertThat(routeLedToBy("/posts/123")).isEqualTo("/posts/{post}")
        assertThat(routeLedToBy("/docs/intro")).isEqualTo("/docs/intro")
        // A trailing slash is only added or removed if the path as written doesn't match any page
        assertThat(routeLedToBy("/blog")).isEqualTo("/blog")
        assertThat(routeLedToBy("/blog/")).isEqualTo("/blog/")
        // Segments that aren't known until runtime only match dynamic segments
        assertThat(routeLedToBy("/posts/$")).isEqualTo("/posts/{post}")
        assertThat(routeLedToBy("/docs/$")).isEqualTo("/docs/{...path}")
    }

    @Test
    fun unknownSegmentsDontLeadToStaticPages() {
        val pages = listOf(PageDefinition(fqn = "/posts/new", route = "/posts/new", function = null))
        // Even though "$id" could be "new" at runtime
        assertThat(pages.findLedToBy(path("/posts/$")!!)).isNull()
    }
}
