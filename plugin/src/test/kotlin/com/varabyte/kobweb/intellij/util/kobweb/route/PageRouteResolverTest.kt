package com.varabyte.kobweb.intellij.util.kobweb.route

import com.varabyte.truthish.assertThat
import org.jetbrains.kotlin.name.FqName
import org.junit.Test

class PageRouteResolverTest {
    private fun resolve(
        pagePackage: String,
        fileName: String,
        routeOverride: String? = null,
        packageMappings: Map<String, String> = emptyMap(),
    ): String? {
        return PageRouteResolver(FqName("com.example.pages"), packageMappings.mapKeys { (pkg, _) -> FqName(pkg) })
            .resolve(FqName(pagePackage), fileName, routeOverride)
    }

    @Test
    fun routesComeFromPackageAndFileName() {
        assertThat(resolve("com.example.pages", "About")).isEqualTo("/about")
        assertThat(resolve("com.example.pages.blog", "Post")).isEqualTo("/blog/post")
        assertThat(resolve("com.example.pages.blog.drafts", "Post")).isEqualTo("/blog/drafts/post")
    }

    @Test
    fun namesAreConvertedToKebabCase() {
        assertThat(resolve("com.example.pages.ourTeam", "WelcomeIntro")).isEqualTo("/our-team/welcome-intro")
        assertThat(resolve("com.example.pages", "AboutABC")).isEqualTo("/about-abc")
        // Underscores usually work around names Kotlin doesn't allow, e.g. ones starting with a digit or reserved words
        assertThat(resolve("com.example.pages._2021._12", "Recap")).isEqualTo("/2021/12/recap")
        assertThat(resolve("com.example.pages.fun_", "Games")).isEqualTo("/fun/games")
        assertThat(resolve("com.example.pages", "Hello__World")).isEqualTo("/hello-world")
    }

    @Test
    fun indexPagesAreServedAtTheirParentRoute() {
        assertThat(resolve("com.example.pages", "Index")).isEqualTo("/")
        assertThat(resolve("com.example.pages.blog", "Index")).isEqualTo("/blog/")
    }

    @Test
    fun routeOverridesMatchKobwebDocs() {
        // Examples from the docs of Kobweb's `Page` annotation
        fun resolve(routeOverride: String?) = resolve("com.example.pages.a.b.c", "Slug", routeOverride)

        assertThat(resolve(null)).isEqualTo("/a/b/c/slug")
        assertThat(resolve("other")).isEqualTo("/a/b/c/other")
        assertThat(resolve("index")).isEqualTo("/a/b/c/")
        assertThat(resolve("d/e/f/")).isEqualTo("/a/b/c/d/e/f/slug")
        assertThat(resolve("d/e/f/other")).isEqualTo("/a/b/c/d/e/f/other")
        assertThat(resolve("/d/e/f/")).isEqualTo("/d/e/f/slug")
        assertThat(resolve("/")).isEqualTo("/slug")

        assertThat(resolve("{}")).isEqualTo("/a/b/c/{slug}")
        assertThat(resolve("{slug}")).isEqualTo("/a/b/c/{slug}")
        assertThat(resolve("d/e/f/{}")).isEqualTo("/a/b/c/d/e/f/{slug}")
        assertThat(resolve("/d/e/f/{}")).isEqualTo("/d/e/f/{slug}")
        assertThat(resolve("/d/{id}/f/{}")).isEqualTo("/d/{id}/f/{slug}")
    }

    @Test
    fun blankRouteOverridesAreIgnored() {
        assertThat(resolve("com.example.pages.blog", "Post", "")).isEqualTo("/blog/post")
        assertThat(resolve("com.example.pages.blog", "Post", "  ")).isEqualTo("/blog/post")
    }

    @Test
    fun inferredDynamicSegmentsKeepTheirModifiers() {
        assertThat(resolve("com.example.pages.docs", "Path", "{...}")).isEqualTo("/docs/{...path}")
        assertThat(resolve("com.example.pages.users", "Id", "{?}")).isEqualTo("/users/{id?}")
        assertThat(resolve("com.example.pages.docs", "Path", "{...?}")).isEqualTo("/docs/{...path?}")
    }

    @Test
    fun onlyTheLastSegmentCanBeInferredCatchAllOrOptional() {
        assertThat(resolve("com.example.pages", "Post", "/a/{}/c/{post}")).isNull()
        assertThat(resolve("com.example.pages", "Post", "/a/{...rest}/c")).isNull()
        assertThat(resolve("com.example.pages", "Post", "a/{id?}/c")).isNull()
        // Named, required segments are fine anywhere
        assertThat(resolve("com.example.pages", "Post", "/a/{id}/c/{}")).isEqualTo("/a/{id}/c/{post}")
    }

    @Test
    fun pagesOutsideThePagesPackageNeedAnAbsoluteRouteOverride() {
        assertThat(resolve("com.example.components", "Widget")).isNull()
        assertThat(resolve("com.example.components", "Widget", "widget")).isNull()
        assertThat(resolve("com.example.components", "Widget", "/demos/")).isEqualTo("/demos/widget")
        // Only whole package names count, so "pages_old" isn't under "pages"
        assertThat(resolve("com.example.pages_old", "About")).isNull()
    }

    @Test
    fun packageMappingsReplacePackageNames() {
        val packageMappings = mapOf(
            "com.example.pages.blogs._2021" to "twenty-twenty-one",
            "com.example.pages.users" to "{}",
            "com.example.pages.teams" to "{team-id}",
        )
        fun resolve(pagePackage: String, fileName: String, routeOverride: String? = null) =
            resolve(pagePackage, fileName, routeOverride, packageMappings)

        assertThat(resolve("com.example.pages.blogs._2021", "Recap")).isEqualTo("/blogs/twenty-twenty-one/recap")
        assertThat(resolve("com.example.pages.blogs._2021.drafts", "Recap"))
            .isEqualTo("/blogs/twenty-twenty-one/drafts/recap")
        assertThat(resolve("com.example.pages.users", "Posts")).isEqualTo("/{users}/posts")
        assertThat(resolve("com.example.pages.teams", "Members")).isEqualTo("/{team-id}/members")
        // Absolute overrides replace the package's route entirely
        assertThat(resolve("com.example.pages.users", "Posts", "/all/")).isEqualTo("/all/posts")
    }
}
