@file:Suppress("FunctionName", "RedundantSuspendModifier")

package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PsiTestUtil
import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

class ConflictingRoutesInspectionTest : KobwebFullStackTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ConflictingRoutesInspection())

        addFileToProject(
            Platform.Js,
            "pages/About.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun AboutPage() {
            }
            """.trimIndent()
        )

        addFileToProject(
            Platform.Jvm,
            "api/Hello.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api
            suspend fun hello(ctx: ApiContext) {
            }
            """.trimIndent()
        )

        addFileToProject(
            Platform.Jvm,
            "api/Chat.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.stream.ApiStream

            val chat = ApiStream { ctx -> }
            """.trimIndent()
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun checkHighlighting(platform: Platform, fileName: String, @Language("kotlin") code: String) {
        configureByText(platform, fileName, code)
        allowAnalysisOnEdt { myFixture.checkHighlighting() }
    }

    fun testPagesAtTheSameRouteAreFlagged() {
        checkHighlighting(
            Platform.Js,
            "Company.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("/about")
            @Composable
            fun <error descr="This page's route (\"/about\") is also used by `$TEST_GROUP.pages.AboutPage`, which will fail the build.">CompanyPage</error>() {
            }

            @Page("/team")
            @Composable
            fun TeamPage() {
            }
            """.trimIndent()
        )
    }

    fun testApiEndpointsAtTheSameRouteAreFlagged() {
        checkHighlighting(
            Platform.Jvm,
            "Greeting.kt",
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api("hello")
            suspend fun <error descr="This API endpoint's route (\"/api/hello\") is also used by `$TEST_GROUP.api.hello`, which will fail the build.">greeting</error>(ctx: ApiContext) {
            }

            @Api("goodbye")
            suspend fun farewell(ctx: ApiContext) {
            }

            // API endpoints and streams are registered separately, so they don't conflict
            @Api("chat")
            suspend fun chatHistory(ctx: ApiContext) {
            }
            """.trimIndent()
        )
    }

    fun testStreamsAtTheSameRouteAreFlagged() {
        checkHighlighting(
            Platform.Jvm,
            "Talk.kt",
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.stream.ApiStream

            @Api("chat")
            val <error descr="This API stream's route (\"chat\") is also used by `$TEST_GROUP.api.chat`, which will fail the build.">talk</error> = ApiStream { ctx -> }

            val talk2 = ApiStream { ctx -> }
            """.trimIndent()
        )
    }

    private fun addProfilePage() {
        addFileToProject(
            Platform.Js,
            "pages/users/Profile.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("{id}")
            @Composable
            fun ProfilePage() {
            }
            """.trimIndent()
        )
    }

    fun testPagesWithDifferentDynamicSegmentsInTheSamePositionAreFlagged() {
        addProfilePage()

        checkHighlighting(
            Platform.Js,
            "Settings.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("{name}/settings")
            @Composable
            fun <error descr="This page's route (\"/users/{name}/settings\") uses \"{name}\" where the route of `$TEST_GROUP.pages.users.ProfilePage` (\"/users/{id}\") uses \"{id}\", which will fail the build, as only one dynamic segment can be registered in the same position. Please use the same dynamic segment in both.">SettingsPage</error>() {
            }
            """.trimIndent()
        )
    }

    fun testPagesThatShareADynamicSegmentAreNotFlagged() {
        addProfilePage()

        checkHighlighting(
            Platform.Js,
            "Posts.kt",
            """
            package $TEST_GROUP.pages.users

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            // Shares the dynamic segment, so it doesn't conflict
            @Page("{id}/posts")
            @Composable
            fun PostsPage() {
            }

            // Static segments can sit next to a dynamic one
            @Page("new")
            @Composable
            fun NewUserPage() {
            }
            """.trimIndent()
        )
    }

    fun testApiEndpointsWithDifferentDynamicSegmentsInTheSamePositionAreFlagged() {
        addFileToProject(
            Platform.Jvm,
            "api/users/User.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api("{id}")
            suspend fun user(ctx: ApiContext) {
            }
            """.trimIndent()
        )

        checkHighlighting(
            Platform.Jvm,
            "api/users/Avatar.kt",
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api("{...path}")
            suspend fun <error descr="This API endpoint's route (\"/api/users/{...path}\") uses \"{...path}\" where the route of `$TEST_GROUP.api.users.user` (\"/api/users/{id}\") uses \"{id}\", which will fail the build, as only one dynamic segment can be registered in the same position. Please use the same dynamic segment in both.">avatar</error>(ctx: ApiContext) {
            }
            """.trimIndent()
        )
    }

    fun testTrailingOptionalSegmentsThatAreNeverEmptyAreFlagged() {
        addFileToProject(
            Platform.Js,
            "pages/blog/Index.kt",
            // language=kotlin
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun BlogPage() {
            }
            """.trimIndent()
        )

        checkHighlighting(
            Platform.Js,
            "Post.kt",
            """
            package $TEST_GROUP.pages.blog

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page("{post?}")
            @Composable
            fun <error descr="This page's route (\"/blog/{post?}\") ends with an optional segment, but `$TEST_GROUP.pages.blog.BlogPage` is registered at \"/blog/\", which always takes precedence when the segment is empty. This will fail the build, so please make the segment required instead, i.e. \"{post}\".">PostPage</error>() {
            }

            // Nothing is registered at "/drafts/", so the segment can be empty
            @Page("/drafts/{draft?}")
            @Composable
            fun DraftPage() {
            }
            """.trimIndent()
        )
    }

    fun testPagesAtTheSameRouteAsAPublicFileAreFlagged() {
        val resourcesRoot = myFixture.addFileToProject("resources/public/resume", "").virtualFile.parent.parent
        myFixture.addFileToProject("resources/public/blog/cat.png", "")
        PsiTestUtil.addSourceRoot(module, resourcesRoot, JavaResourceRootType.RESOURCE)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeSourceRoot(module, resourcesRoot) }

        checkHighlighting(
            Platform.Js,
            "Resume.kt",
            """
            package $TEST_GROUP.pages

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun <warning descr="A public file is also served at this page's route (\"/resume\"). Visiting the URL directly will show the file, but navigating to it within the site will show the page.">ResumePage</warning>() {
            }

            // The server never serves folders
            @Page("/blog")
            @Composable
            fun BlogPage() {
            }

            // Dynamic routes are left alone, even if they match a public file
            @Page("/{name}")
            @Composable
            fun NamePage() {
            }
            """.trimIndent()
        )
    }
}
