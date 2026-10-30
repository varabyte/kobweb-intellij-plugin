package com.varabyte.kobweb.intellij.util.kobweb.route

import com.varabyte.kobweb.intellij.util.kobweb.route.RouteValueSimplification.Redundant
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteValueSimplification.Shorten
import com.varabyte.truthish.assertThat
import org.jetbrains.kotlin.name.FqName
import org.junit.Test

class RouteValueSimplificationTest {
    private val resolver = PageRouteResolver(FqName("com.example.pages"), emptyMap())

    private fun simplifyRouteOverride(pagePackage: String, fileName: String, routeOverride: String) =
        RouteValueSimplification.forRouteOverride(fileName, routeOverride) { override ->
            resolver.resolve(FqName(pagePackage), fileName, override)
        }

    private fun simplifyPackageMapping(pkg: String, packageMapping: String) =
        RouteValueSimplification.forPackageMapping(FqName(pkg), packageMapping)

    @Test
    fun routeOverridesMatchingTheDefaultRouteAreRedundant() {
        assertThat(simplifyRouteOverride("com.example.pages", "SomeSlug", "some-slug")).isEqualTo(Redundant)
        assertThat(simplifyRouteOverride("com.example.pages", "Index", "index")).isEqualTo(Redundant)
        assertThat(simplifyRouteOverride("com.example.pages.blog", "Post", "/blog/post")).isEqualTo(Redundant)
        assertThat(simplifyRouteOverride("com.example.pages.blog", "Post", "/blog/")).isEqualTo(Redundant)
        // Kobweb ignores blank overrides
        assertThat(simplifyRouteOverride("com.example.pages.blog", "Post", "")).isEqualTo(Redundant)
    }

    @Test
    fun dynamicSegmentsNamedAfterTheFileCanBeInferred() {
        assertThat(simplifyRouteOverride("com.example.pages", "SomeSlug", "{some-slug}")).isEqualTo(Shorten("{}"))
        assertThat(simplifyRouteOverride("com.example.pages.blog", "Post", "/blog/{post}")).isEqualTo(Shorten("{}"))
        assertThat(simplifyRouteOverride("com.example.pages.blog", "Post", "/other/{post}"))
            .isEqualTo(Shorten("/other/{}"))
        // Modifiers are kept
        assertThat(simplifyRouteOverride("com.example.pages.docs", "Path", "{...path}")).isEqualTo(Shorten("{...}"))
        assertThat(simplifyRouteOverride("com.example.pages.users", "Id", "{id?}")).isEqualTo(Shorten("{?}"))
    }

    @Test
    fun routeOverridesThatChangeTheRouteCantBeSimplified() {
        assertThat(simplifyRouteOverride("com.example.pages.blog", "Post", "article")).isNull()
        assertThat(simplifyRouteOverride("com.example.pages.blog", "Post", "{id}")).isNull()
        assertThat(simplifyRouteOverride("com.example.pages.blog", "Post", "{}")).isNull()
        assertThat(simplifyRouteOverride("com.example.pages.blog", "Post", "/other/post")).isNull()
        // Kobweb skips this page, so there's no route to compare against
        assertThat(simplifyRouteOverride("com.example.pages.blog", "Post", "/a/{}/post")).isNull()
    }

    @Test
    fun packageMappingsMatchingThePackageNameAreRedundant() {
        assertThat(simplifyPackageMapping("com.example.pages.blog", "blog")).isEqualTo(Redundant)
        assertThat(simplifyPackageMapping("com.example.pages.ourTeam", "our-team")).isEqualTo(Redundant)
        assertThat(simplifyPackageMapping("com.example.pages._2021", "2021")).isEqualTo(Redundant)
    }

    @Test
    fun dynamicPackageMappingsNamedAfterThePackageCanBeInferred() {
        assertThat(simplifyPackageMapping("com.example.pages.user", "{user}")).isEqualTo(Shorten("{}"))
        assertThat(simplifyPackageMapping("com.example.pages.someRoute", "{some-route}")).isEqualTo(Shorten("{}"))
    }

    @Test
    fun packageMappingsThatChangeTheRouteCantBeSimplified() {
        assertThat(simplifyPackageMapping("com.example.pages.blog", "articles")).isNull()
        assertThat(simplifyPackageMapping("com.example.pages.user", "{id}")).isNull()
        assertThat(simplifyPackageMapping("com.example.pages.user", "{}")).isNull()
        // A blank mapping changes routes (and is reported as invalid instead)
        assertThat(simplifyPackageMapping("com.example.pages.blog", "")).isNull()
    }
}
