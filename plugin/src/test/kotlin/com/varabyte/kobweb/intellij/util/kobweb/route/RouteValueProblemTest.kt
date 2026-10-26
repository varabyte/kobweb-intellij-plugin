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
            "d/e/f/other", "{?}", "{...?}", "{user-id}", "{user_id}", "{userId2}",
            // Dynamic segment names never appear in the URL, so they aren't limited to ASCII
            "{café}",
            // Percent-encoded characters are fine
            "about%20us", "%7Bx%7D", "caf%C3%A9", "a-b_c.d~e!$&'()*+,;=:@",
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
    fun strayCurlyBracesAndMalformedDynamicSegmentsAreReported() {
        assertThat(inRouteOverride("/posts/{post")).containsExactly(Kind.STRAY_CURLY_BRACES to "{post")
        assertThat(inRouteOverride("post}")).containsExactly(Kind.STRAY_CURLY_BRACES to "post}")
        assertThat(inRouteOverride("a{b}c")).containsExactly(Kind.STRAY_CURLY_BRACES to "a{b}c")
        // Kobweb accepts this as one dynamic segment named "a}{b"
        assertThat(inRouteOverride("{a}{b}")).containsExactly(Kind.MALFORMED_DYNAMIC_SEGMENT to "{a}{b}")
        assertThat(inRouteOverride("d/{id/e")).containsExactly(Kind.STRAY_CURLY_BRACES to "{id")
        assertThat(inRouteOverride("d/{x/y}/e")).containsExactly(Kind.STRAY_CURLY_BRACES to "{x", Kind.STRAY_CURLY_BRACES to "y}")
    }

    @Test
    fun dynamicSegmentNamesCantContainSpecialCharacters() {
        // From Kobweb's `RouteOverrideTest`. Any leading "..." and trailing "?" aren't part of the name, so the dots
        // and question marks left over in many of these are
        listOf(
            "{a b}", "{a?b}", "{??}", "{rest...}", "{rest...?}", "{.....rest}", "{..rest}", "{.rest}", "{......}",
            "{...rest...}", "{.}", "{a.b}", "{{a}}", "{...{a}}", "{a#b}", "{a+b}", "{a:b}",
        ).forEach { routeOverride ->
            assertThat(inRouteOverride(routeOverride))
                .containsExactly(Kind.MALFORMED_DYNAMIC_SEGMENT to routeOverride)
        }
    }

    @Test
    fun emptySegmentsAreReported() {
        assertThat(inRouteOverride("a//b")).containsExactly(Kind.EMPTY_SEGMENT to "//")
        assertThat(inRouteOverride("//a")).containsExactly(Kind.EMPTY_SEGMENT to "//")
        assertThat(inRouteOverride("a///b")).containsExactly(Kind.EMPTY_SEGMENT to "//", Kind.EMPTY_SEGMENT to "//")
        assertThat(inRouteOverride("//")).containsExactly(Kind.EMPTY_SEGMENT to "//")
        assertThat(inRouteOverride("/a//")).containsExactly(Kind.EMPTY_SEGMENT to "//")
    }

    @Test
    fun dotSegmentsAreReported() {
        assertThat(inRouteOverride("a/../b")).containsExactly(Kind.DOT_SEGMENT to "..")
        // The rest of these are from Kobweb's `RouteOverrideTest`
        listOf(".", "..", "a/./b", "./b", "a/..", "a/../", "%2e", "%2E%2e", ".%2E", "%2e.").forEach { routeOverride ->
            assertThat(inRouteOverride(routeOverride).map { (kind, _) -> kind }).containsExactly(Kind.DOT_SEGMENT)
        }
        listOf("...", ".a", "a.", "a.b", "%2e%2e%2e", ".well-known").forEach { routeOverride ->
            assertThat(inRouteOverride(routeOverride)).isEmpty()
        }
    }

    @Test
    fun invalidCharactersAreReported() {
        assertThat(inRouteOverride("posts?draft")).containsExactly(Kind.INVALID_CHARACTER to "posts?draft")
        assertThat(inRouteOverride("/about#team")).containsExactly(Kind.INVALID_CHARACTER to "about#team")
        assertThat(inRouteOverride("/company/about us/")).containsExactly(Kind.INVALID_CHARACTER to "about us")
        // The rest of these are from Kobweb's `RouteOverrideTest`
        listOf("about\tus", "a<b>", "a\"b", "a`b", "café", "😀", "a\\b").forEach { routeOverride ->
            assertThat(inRouteOverride(routeOverride)).containsExactly(Kind.INVALID_CHARACTER to routeOverride)
        }
    }

    @Test
    fun invalidCharactersAreFoundOnce() {
        assertThat(RouteValueProblem.findInvalidCharacters("a b?c d?é😀é")).containsExactly(" ", "?", "é", "😀").inOrder()
        // Curly braces are reported separately
        assertThat(RouteValueProblem.findInvalidCharacters("{a}")).isEmpty()
    }

    @Test
    fun eachSegmentIsCheckedSeparately() {
        assertThat(inRouteOverride("/{users/{}/posts?all")).containsExactly(
            Kind.STRAY_CURLY_BRACES to "{users",
            Kind.MISPLACED_DYNAMIC_SEGMENT to "{}",
            Kind.INVALID_CHARACTER to "posts?all",
        )
        assertThat(inRouteOverride("a//{b/c?d/e f/{a.b}")).containsExactly(
            Kind.EMPTY_SEGMENT to "//",
            Kind.STRAY_CURLY_BRACES to "{b",
            Kind.INVALID_CHARACTER to "c?d",
            Kind.INVALID_CHARACTER to "e f",
            Kind.MALFORMED_DYNAMIC_SEGMENT to "{a.b}",
        )
        // Stray braces and other invalid characters in a single segment are reported separately
        assertThat(inRouteOverride("{a} b")).containsExactly(
            Kind.STRAY_CURLY_BRACES to "{a} b",
            Kind.INVALID_CHARACTER to "{a} b",
        )
    }

    @Test
    fun packageMappingsCanBeInferredButNotCatchAllOrOptional() {
        assertThat(inPackageMapping("{}")).isEmpty()
        assertThat(inPackageMapping("a/{}")).containsExactly(Kind.MULTIPLE_SEGMENTS to "a/{}")
        assertThat(inPackageMapping("{user}")).isEmpty()
        assertThat(inPackageMapping("our-team")).isEmpty()
        // A blank mapping removes the package's segment from routes, which is fine
        assertThat(inPackageMapping("")).isEmpty()

        assertThat(inPackageMapping("{...}")).containsExactly(Kind.MISPLACED_DYNAMIC_SEGMENT to "{...}")
        assertThat(inPackageMapping("{...rest}")).containsExactly(Kind.MISPLACED_DYNAMIC_SEGMENT to "{...rest}")
        assertThat(inPackageMapping("{id?}")).containsExactly(Kind.MISPLACED_DYNAMIC_SEGMENT to "{id?}")
    }

    @Test
    fun eachSegmentOfAPackageMappingIsChecked() {
        // From Kobweb's `PackageMappingProcessingTest`
        assertThat(inPackageMapping("a/{...rest}")).containsExactly(
            Kind.MULTIPLE_SEGMENTS to "a/{...rest}",
            Kind.MISPLACED_DYNAMIC_SEGMENT to "{...rest}",
        )
        assertThat(inPackageMapping("{...a}/b")).containsExactly(
            Kind.MULTIPLE_SEGMENTS to "{...a}/b",
            Kind.MISPLACED_DYNAMIC_SEGMENT to "{...a}",
        )
        // Only the last segment can be inferred
        assertThat(inPackageMapping("{}/{}")).containsExactly(
            Kind.MULTIPLE_SEGMENTS to "{}/{}",
            Kind.MISPLACED_DYNAMIC_SEGMENT to "{}",
        )
        assertThat(inPackageMapping("{}/b")).containsExactly(
            Kind.MULTIPLE_SEGMENTS to "{}/b",
            Kind.MISPLACED_DYNAMIC_SEGMENT to "{}",
        )
    }

    @Test
    fun packageMappingsGetTheSameSyntaxChecks() {
        assertThat(inPackageMapping("{user")).containsExactly(Kind.STRAY_CURLY_BRACES to "{user")
        assertThat(inPackageMapping("a?b")).containsExactly(Kind.INVALID_CHARACTER to "a?b")
    }

    @Test
    fun packageMappingsOnlyWarnAboutSlashes() {
        fun errorsInPackageMapping(value: String) = RouteValueProblem.findInPackageMapping(value).map { it.isError }

        assertThat(errorsInPackageMapping("a/b")).containsExactly(false)
        assertThat(errorsInPackageMapping("a/{...rest}")).containsExactly(false, true).inOrder()
        assertThat(errorsInPackageMapping("{...rest}")).containsExactly(true)
    }

    @Test
    fun packageMappingsShouldBeASingleSegment() {
        assertThat(inPackageMapping("a/b")).containsExactly(Kind.MULTIPLE_SEGMENTS to "a/b")
        assertThat(inPackageMapping("our//team")).containsExactly(
            Kind.MULTIPLE_SEGMENTS to "our//team",
            Kind.EMPTY_SEGMENT to "//",
        )
    }
}
