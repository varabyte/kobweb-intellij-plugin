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

    @Test
    fun conflictingDynamicSegments() {
        fun conflict(a: String, b: String) = PageRoute(a).findConflictingDynamicSegments(PageRoute(b))

        assertThat(conflict("/users/{id}", "/users/{name}")).isEqualTo("{id}" to "{name}")
        assertThat(conflict("/users/{id}", "/users/{name}/posts")).isEqualTo("{id}" to "{name}")
        assertThat(conflict("/{a}/x", "/{b}/y")).isEqualTo("{a}" to "{b}")
        assertThat(conflict("/docs/{page}", "/docs/{...rest}")).isEqualTo("{page}" to "{...rest}")
        assertThat(conflict("/docs/{page}", "/docs/{page?}")).isEqualTo("{page}" to "{page?}")

        // The same dynamic segment is shared, so the routes only diverge later (or not at all)
        assertThat(conflict("/users/{id}", "/users/{id}/posts")).isNull()
        assertThat(conflict("/users/{id}", "/users/{id}")).isNull()
        // A static segment and a dynamic one can sit side by side
        assertThat(conflict("/users/{id}", "/users/new")).isNull()
        // Different static segments mean the routes never meet again
        assertThat(conflict("/a/{x}", "/b/{y}")).isNull()
    }
}
