@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.route

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase

class InvalidRouteFormatInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = InvalidRouteFormatInspection()

    fun testFullUrlsAreFlaggedForPathParameters() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.navigation.Router
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav(router: Router) {
                // Links and navigation can go to other sites
                Link("https://example.com/blog")
                router.navigateTo("https://example.com/blog")
                router.tryRoutingTo("/blog/post?sort=new#comments")
                router.tryRoutingTo("<error descr="Only a path within the site, such as \"/some/page\", is expected here, not a full URL.">https://example.com/blog</error>")
                router.tryRoutingTo("<error>//example.com/blog</error>")
                router.tryRoutingTo("<error>mailto:me@example.com</error>")
            }
            """.trimIndent(),
        )
    }

    fun testQueryParametersAndFragmentsAreFlaggedForPathOnlyParameters() {
        doTest(
            $$"""
            package $$TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.RoutePath
            import com.varabyte.kobweb.core.RoutePathType

            @Composable
            fun Breadcrumb(@RoutePath(RoutePathType.PATH_ONLY) path: String) {
            }

            @Composable
            fun Nav(id: String) {
                Breadcrumb("/blog/post")
                Breadcrumb("/blog/post<error descr="Only a path, such as \"/some/page\", is expected here, without query parameters or a fragment.">?sort=new</error>")
                Breadcrumb("/blog/post<error>#comments</error>")
                Breadcrumb("/posts/$id<error>?sort=new</error>")
                Breadcrumb("<error>https://example.com</error>")
            }
            """.trimIndent(),
        )
    }

    fun testParametersDefaultToAcceptingAnyUrl() {
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.RoutePath

            @Composable
            fun NavItem(@RoutePath target: String) {
            }

            @Composable
            fun Nav() {
                NavItem("https://example.com/blog?sort=new")
                // Not passed to a parameter that takes a route
                println("https://example.com/blog")
            }
            """.trimIndent(),
        )
    }

    fun testComparisonsAreCheckedAgainstWhatThePropertyHolds() {
        doTest(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.core.RoutePath
            import com.varabyte.kobweb.core.RoutePathType

            class CurrentRoute(
                @property:RoutePath(RoutePathType.PATH_ONLY) val path: String,
                @get:RoutePath(RoutePathType.PATH_QUERY_AND_FRAGMENT) val pathQueryAndFragment: String,
            )

            fun check(route: CurrentRoute) {
                println(route.path == "/blog/post")
                println(route.path == "/blog/post<error descr="Only a path, such as \"/some/page\", is expected here, without query parameters or a fragment.">?sort=new</error>")
                println(route.pathQueryAndFragment == "/blog/post?sort=new")
                println(route.pathQueryAndFragment != "<error descr="Only a path within the site, such as \"/some/page\", is expected here, not a full URL.">https://example.com/blog</error>")
                when (route.path) {
                    "/blog/post<error>#comments</error>" -> println("Comments")
                }
            }
            """.trimIndent(),
        )
    }

    fun testComparisonsWithCurrentRouteAreChecked() {
        // The version of Kobweb we test against doesn't annotate these properties yet, but we recognize them anyway
        doTest(
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.rememberPageContext

            @Composable
            fun Nav() {
                val ctx = rememberPageContext()
                println(ctx.route.path == "/blog/post<error>?sort=new</error>")
                println(ctx.route.pathQueryAndFragment == "/blog/post?sort=new")
                println(ctx.route.pathQueryAndFragment == "<error>https://example.com/blog</error>")
            }
            """.trimIndent(),
        )
    }

    fun testAssignmentsAreCheckedAgainstWhatThePropertyTakes() {
        doTest(
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.navigation.Router

            fun interceptRoutes(router: Router) {
                router.addRouteInterceptor {
                    path = "/blog/post"
                    path = "/blog/post<error descr="Only a path, such as \"/some/page\", is expected here, without query parameters or a fragment.">?sort=new</error>"
                }
            }
            """.trimIndent(),
        )
    }
}
