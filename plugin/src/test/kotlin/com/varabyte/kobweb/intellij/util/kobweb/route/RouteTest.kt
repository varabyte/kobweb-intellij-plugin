package com.varabyte.kobweb.intellij.util.kobweb.route

import com.varabyte.truthish.assertThat
import org.junit.Test

class RouteTest {
    @Test
    fun segments() {
        assertThat(PageRoute("/blog/post").segments).isEqualTo(listOf("blog", "post"))
        assertThat(PageRoute("/users/{user}/posts").segments).isEqualTo(listOf("users", "{user}", "posts"))
        // A trailing slash (i.e. an index page) leaves an empty last segment
        assertThat(PageRoute("/blog/").segments).isEqualTo(listOf("blog", ""))
        assertThat(PageRoute("/").segments).isEqualTo(listOf(""))
    }

    @Test
    fun hasDynamicSegments() {
        assertThat(PageRoute("/users/{user}/posts").hasDynamicSegments).isTrue()
        assertThat(PageRoute("/docs/{...path}").hasDynamicSegments).isTrue()
        assertThat(PageRoute("/blog/post").hasDynamicSegments).isFalse()
        assertThat(PageRoute("/").hasDynamicSegments).isFalse()
    }

    @Test
    fun mostSpecificRouteHasStaticSegmentsInTheEarliestPositions() {
        fun pick(vararg routes: String) = routes.toList().pickMostSpecific { PageRoute(it) }

        assertThat(pick("/posts/{post}", "/posts/new")).isEqualTo("/posts/new")
        assertThat(pick("/{a}/b", "/a/{b}")).isEqualTo("/a/{b}")
        assertThat(pick()).isNull()
    }
}
