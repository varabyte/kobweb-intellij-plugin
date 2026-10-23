package com.varabyte.kobweb.intellij.util.kobweb.route

import com.varabyte.truthish.assertThat
import org.junit.Test

class PageRouteTest {
    @Test
    fun segments() {
        assertThat(PageRoute("/blog/post").segments).isEqualTo(listOf("blog", "post"))
        assertThat(PageRoute("/users/{user}/posts").segments).isEqualTo(listOf("users", "{user}", "posts"))
        // A trailing slash (i.e. an index page) leaves an empty last segment
        assertThat(PageRoute("/blog/").segments).isEqualTo(listOf("blog", ""))
        assertThat(PageRoute("/").segments).isEqualTo(listOf(""))
    }
}
