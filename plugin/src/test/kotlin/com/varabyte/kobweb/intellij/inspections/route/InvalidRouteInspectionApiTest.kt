@file:Suppress("FunctionName", "RedundantSuspendModifier")

package com.varabyte.kobweb.intellij.inspections.route

import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

/** Tests for [InvalidRouteInspection] on the routes of API endpoints and streams. */
class InvalidRouteInspectionApiTest : KobwebFullStackTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(InvalidRouteInspection())
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun checkHighlighting(fileName: String, @Language("kotlin") code: String) {
        configureByText(Platform.Jvm, fileName, code)
        allowAnalysisOnEdt { myFixture.checkHighlighting() }
    }

    fun testValidApiRoutesAreNotFlagged() {
        checkHighlighting(
            "api/users/User.kt",
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext
            import com.varabyte.kobweb.api.stream.ApiStream

            @Api
            suspend fun list(ctx: ApiContext) {
            }

            @Api("{}")
            suspend fun user(ctx: ApiContext) {
            }

            @Api("/status")
            suspend fun status(ctx: ApiContext) {
            }

            val updates = ApiStream { ctx -> }
            """.trimIndent()
        )
    }

    fun testApiRouteOverridesGetSyntaxChecks() {
        checkHighlighting(
            "api/users/Posts.kt",
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext
            import com.varabyte.kobweb.api.stream.ApiStream

            @Api("<error descr="Inferred (`{}`), catch-all (`{...}`), and optional (`{?}`) dynamic segments are only allowed at the end of a route.">{}</error>/posts")
            suspend fun userPosts(ctx: ApiContext) {
            }

            @Api("a<error descr="Routes can't contain empty segments.">//</error>b")
            suspend fun empty(ctx: ApiContext) {
            }

            @Api("<error>{post</error>")
            val posts = ApiStream { ctx -> }
            """.trimIndent()
        )
    }

    fun testApiEndpointsAndStreamsOutsideApiPackageNeedAnAbsoluteRoute() {
        checkHighlighting(
            "server/Status.kt",
            """
            package $TEST_GROUP.server

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext
            import com.varabyte.kobweb.api.stream.ApiStream

            <error descr="API endpoints and streams must be declared under the api package (`$TEST_GROUP.api`), unless their `@Api` route override is absolute (i.e. starts with a slash).">@Api</error>
            suspend fun status(ctx: ApiContext) {
            }

            @Api(<error>"health"</error>)
            suspend fun health(ctx: ApiContext) {
            }

            @Api("/version")
            suspend fun version(ctx: ApiContext) {
            }

            val <error>events</error> = ApiStream { ctx -> }

            @Api("/news")
            val news = ApiStream { ctx -> }
            """.trimIndent()
        )
    }

    fun testRepeatedDynamicSegmentNamesInApiRoutesAreFlagged() {
        checkHighlighting(
            "api/users/User.kt",
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api(<error descr="This API endpoint's route (\"/api/users/{id}/{id}\") uses the dynamic segment name(s) \"id\" more than once, so only one of the values can be read. Please rename one of them.">"{id}/{id}"</error>)
            suspend fun user(ctx: ApiContext) {
            }
            """.trimIndent()
        )
    }

    fun testStreamsWithDynamicSegmentsAreFlagged() {
        checkHighlighting(
            "api/Chat.kt",
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.stream.ApiStream

            @Api(<error descr="This API stream's route (\"rooms/{room}\") has dynamic segments, but streams are connected to by their exact route, so they can't have any.">"rooms/{room}"</error>)
            val chat = ApiStream { ctx -> }

            @Api("rooms/lobby")
            val lobby = ApiStream { ctx -> }
            """.trimIndent()
        )
    }

    fun testApiPackageMappingsAreChecked() {
        checkHighlighting(
            "api/users/PackageMapping.kt",
            """
            @file:PackageMapping("<error descr="Package mappings can't use catch-all (`{...}`) or optional (`{?}`) dynamic segments, as a package is never at the end of a route.">{...rest}</error>")

            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.PackageMapping
            """.trimIndent()
        )
    }

    fun testApiPackageMappingsOutsideApiPackageAreFlagged() {
        checkHighlighting(
            "server/PackageMapping.kt",
            """
            <error descr="API package mappings must be declared under the api package (`$TEST_GROUP.api`).">@file:PackageMapping("backend")</error>

            package $TEST_GROUP.server

            import com.varabyte.kobweb.api.PackageMapping
            """.trimIndent()
        )
    }

    fun testApiPackagesMappedMoreThanOnceAreErrors() {
        addFileToProject(
            Platform.Jvm,
            "api/users/OtherPackageMapping.kt",
            // language=kotlin
            """
            @file:PackageMapping("members")

            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.PackageMapping
            """.trimIndent()
        )

        checkHighlighting(
            "api/users/PackageMapping.kt",
            """
            <error>@file:PackageMapping("people")</error>

            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.PackageMapping
            """.trimIndent()
        )
    }
}
