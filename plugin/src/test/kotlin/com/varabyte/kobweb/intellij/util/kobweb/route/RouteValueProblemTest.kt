package com.varabyte.kobweb.intellij.util.kobweb.route

import com.varabyte.kobweb.intellij.util.kobweb.route.RouteValueProblem.Kind
import com.varabyte.truthish.assertThat
import org.junit.Test

class RouteValueProblemTest {
    // Problems as (kind, highlighted text) pairs, which are easier to read than ranges
    private fun inRouteOverride(value: String) =
        RouteValueProblem.findInRouteOverride(value).map { it.kind to it.range.substring(value) }

    private fun inPackageMapping(value: String) =
        RouteValueProblem.findInPackageMapping(value).map { it.kind to it.range.substring(value) }

    @Test
    fun validRouteOverridesHaveNoProblems() {
        listOf(
            "", "  ", "/", "other", "index", "d/e/f/", "/d/e/f/other", "/x/y/z/{}", "{}", "{slug}", "/d/{id}/f/{}",
            "{...}", "{...rest}", "{id?}", "{...rest?}", "blog-post", "2021", "AboutUs", "{postId}",
        ).forEach { routeOverride ->
            assertThat(inRouteOverride(routeOverride)).isEmpty()
        }
    }

    @Test
    fun onlyTheLastSegmentOfARouteOverrideCanBeInferredCatchAllOrOptional() {
        assertThat(inRouteOverride("/users/{}/posts")).containsExactly(Kind.MISPLACED_DYNAMIC_SEGMENT to "{}")
        assertThat(inRouteOverride("a/{...rest}/b")).containsExactly(Kind.MISPLACED_DYNAMIC_SEGMENT to "{...rest}")
        assertThat(inRouteOverride("a/{id?}/b")).containsExactly(Kind.MISPLACED_DYNAMIC_SEGMENT to "{id?}")
        // With a trailing slash, the page's slug comes last
        assertThat(inRouteOverride("{...rest}/")).containsExactly(Kind.MISPLACED_DYNAMIC_SEGMENT to "{...rest}")
        // Named, required segments are fine anywhere
        assertThat(inRouteOverride("/users/{user}/posts/{}")).isEmpty()
    }

    @Test
    fun malformedDynamicSegmentsAreReported() {
        assertThat(inRouteOverride("/posts/{post")).containsExactly(Kind.MALFORMED_DYNAMIC_SEGMENT to "{post")
        assertThat(inRouteOverride("post}")).containsExactly(Kind.MALFORMED_DYNAMIC_SEGMENT to "post}")
        assertThat(inRouteOverride("a{b}c")).containsExactly(Kind.MALFORMED_DYNAMIC_SEGMENT to "a{b}c")
        // Kobweb accepts this as one dynamic segment named "a}{b"
        assertThat(inRouteOverride("{a}{b}")).containsExactly(Kind.MALFORMED_DYNAMIC_SEGMENT to "{a}{b}")
    }

    @Test
    fun emptySegmentsAreReported() {
        assertThat(inRouteOverride("a//b")).containsExactly(Kind.EMPTY_SEGMENT to "//")
        assertThat(inRouteOverride("//a")).containsExactly(Kind.EMPTY_SEGMENT to "//")
        assertThat(inRouteOverride("a///b")).containsExactly(Kind.EMPTY_SEGMENT to "//", Kind.EMPTY_SEGMENT to "//")
    }

    @Test
    fun invalidCharactersAreReported() {
        assertThat(inRouteOverride("posts?draft")).containsExactly(Kind.INVALID_CHARACTER to "posts?draft")
        assertThat(inRouteOverride("/about#team")).containsExactly(Kind.INVALID_CHARACTER to "about#team")
    }

    @Test
    fun eachSegmentIsCheckedSeparately() {
        assertThat(inRouteOverride("/{users/{}/posts?all")).containsExactly(
            Kind.MALFORMED_DYNAMIC_SEGMENT to "{users",
            Kind.MISPLACED_DYNAMIC_SEGMENT to "{}",
            Kind.INVALID_CHARACTER to "posts?all",
        )
    }

    @Test
    fun packageMappingsCanBeInferredButNotCatchAllOrOptional() {
        assertThat(inPackageMapping("{}")).isEmpty()
        assertThat(inPackageMapping("{user}")).isEmpty()
        assertThat(inPackageMapping("our-team")).isEmpty()
        // A blank mapping removes the package's segment from routes, which is fine
        assertThat(inPackageMapping("")).isEmpty()

        assertThat(inPackageMapping("{...}")).containsExactly(Kind.MISPLACED_DYNAMIC_SEGMENT to "{...}")
        assertThat(inPackageMapping("{...rest}")).containsExactly(Kind.MISPLACED_DYNAMIC_SEGMENT to "{...rest}")
        assertThat(inPackageMapping("{id?}")).containsExactly(Kind.MISPLACED_DYNAMIC_SEGMENT to "{id?}")
    }

    @Test
    fun packageMappingsGetTheSameSyntaxChecks() {
        assertThat(inPackageMapping("{user")).containsExactly(Kind.MALFORMED_DYNAMIC_SEGMENT to "{user")
        assertThat(inPackageMapping("our//team")).containsExactly(Kind.EMPTY_SEGMENT to "//")
    }
}
