@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.route

import com.varabyte.kobweb.intellij.inspections.KobwebInspectionTestBase

class InvalidRouteArgumentInspectionTest : KobwebInspectionTestBase() {
    override fun produceInspection() = InvalidRouteArgumentInspection()

    override fun setUp() {
        super.setUp()

        // Newer versions of Kobweb have this annotation, but the version we test against doesn't
        myFixture.addFileToProject(
            "RoutePath.kt",
            // language=kotlin
            """
            package com.varabyte.kobweb.core

            enum class RoutePathType { URL, PATH_QUERY_AND_FRAGMENT, PATH_ONLY }

            @Target(AnnotationTarget.VALUE_PARAMETER)
            annotation class RoutePath(val type: RoutePathType = RoutePathType.URL, val mustExist: Boolean = true)
            """.trimIndent()
        )
    }

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
                router.tryRoutingTo("<error descr="This parameter only takes a path within the site, such as \"/some/page\", not a full URL.">https://example.com/blog</error>")
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
                Breadcrumb("/blog/post<error descr="This parameter only takes a path, such as \"/some/page\", without query parameters or a fragment.">?sort=new</error>")
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
}
