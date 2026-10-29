@file:Suppress("FunctionName", "RedundantSuspendModifier", "SameParameterValue")

package com.varabyte.kobweb.intellij.completion.api

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PsiTestUtil
import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import com.varabyte.kobweb.intellij.util.kobweb.KOBWEB_METADATA_BACKEND
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

class ApiPathCompletionTest : KobwebFullStackTestCase() {
    override fun setUp() {
        super.setUp()

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
            "api/users/User.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api("{}")
            suspend fun user(ctx: ApiContext) {
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

            val chat = object : ApiStream() {
                override suspend fun onTextReceived(ctx: TextReceivedContext) {
                }
            }
            """.trimIndent()
        )

        addFileToProject(
            Platform.Jvm,
            "api/Echo.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.stream.ApiStream

            val echo = ApiStream { ctx -> ctx.stream.broadcast(ctx.text) }
            """.trimIndent()
        )
    }

    @Language("kotlin")
    private fun produceFetch(path: String) = """
        package $TEST_GROUP.components

        import com.varabyte.kobweb.browser.api
        import kotlinx.browser.window

        suspend fun fetch() {
            window.api.get("$path")
        }
    """.trimIndent()

    @Language("kotlin")
    private fun produceStream(path: String) = """
        package $TEST_GROUP.components

        import com.varabyte.kobweb.streams.ApiStream

        val stream = ApiStream("$path")
    """.trimIndent()

    /**
     * Runs basic completion on [code] (with a `<caret>`) and returns the suggestions, or null if a single suggestion
     * was inserted right away.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun complete(@Language("kotlin") code: String): List<String>? {
        configureByText(Platform.Js, "Fetch.kt", code)
        return allowAnalysisOnEdt { myFixture.completeBasic() }?.map { it.lookupString }
    }

    fun testApiPathsAreSuggestedInEmptyString() {
        assertThat(complete(produceFetch("<caret>"))!!).containsExactly("hello", "users/{user}").inOrder()
    }

    fun testSingleMatchIsInserted() {
        assertThat(complete(produceFetch("he<caret>"))).isNull()
        myFixture.checkResult(produceFetch("hello<caret>"))
    }

    fun testLeadingSlashIsKeptIfTyped() {
        assertThat(complete(produceFetch("/<caret>"))!!).containsExactly("/hello", "/users/{user}").inOrder()
        assertThat(complete(produceFetch("/he<caret>"))).isNull()
        myFixture.checkResult(produceFetch("/hello<caret>"))
    }

    fun testDynamicSegmentsAreFilledInWithTemplate() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)

        assertThat(complete(produceFetch("us<caret>"))).isNull()
        val templateState = TemplateManagerImpl.getTemplateState(myFixture.editor)!!
        myFixture.type("bob")
        templateState.nextTab()

        myFixture.checkResult(produceFetch("users/bob<caret>"))
    }

    fun testStreamPathsAreSuggested() {
        assertThat(complete(produceStream("<caret>"))!!).containsExactly("chat", "echo").inOrder()
    }

    fun testRememberApiStreamPathsAreSuggested() {
        assertThat(
            complete(
                """
                package $TEST_GROUP.components

                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.streams.rememberApiStream

                @Composable
                fun Chat() {
                    rememberApiStream("<caret>") { ctx -> }
                }
                """.trimIndent()
            )!!
        ).containsExactly("chat", "echo").inOrder()
    }

    fun testStringsComparedWithStreamRoutesAreCompleted() {
        assertThat(
            complete(
                """
                package $TEST_GROUP.components

                import com.varabyte.kobweb.streams.ImmutableApiStream

                fun isChat(stream: ImmutableApiStream) = stream.route == "<caret>"
                """.trimIndent()
            )!!
        ).containsExactly("chat", "echo").inOrder()
    }

    fun testStringsNotPassedToApiParametersAreNotCompleted() {
        assertThat(
            complete(
                """
                package $TEST_GROUP.components

                fun log() {
                    println("he<caret>")
                }
                """.trimIndent()
            ).orEmpty()
        ).isEmpty()
    }

    fun testParametersAnnotatedWithApiPathAreCompleted() {
        // `@ApiPath` and `@ApiStreamPath` are newer than the version of Kobweb these tests use
        addFileToProject(
            Platform.Js,
            "com/varabyte/kobweb/api/ApiPath.kt",
            // language=kotlin
            """
            package com.varabyte.kobweb.api

            annotation class ApiPath(val mustExist: Boolean = true)
            annotation class ApiStreamPath(val mustExist: Boolean = true)
            """.trimIndent()
        )

        assertThat(
            complete(
                """
                package $TEST_GROUP.components

                import com.varabyte.kobweb.api.ApiPath
                import com.varabyte.kobweb.api.ApiStreamPath

                fun fetchLater(@ApiPath(mustExist = false) apiPath: String) {}
                fun connectLater(@ApiStreamPath streamPath: String) {}

                fun load() {
                    fetchLater("<caret>")
                }
                """.trimIndent()
            )!!
        ).containsExactly("hello", "users/{user}").inOrder()

        assertThat(
            complete(
                """
                package $TEST_GROUP.components

                import com.varabyte.kobweb.api.ApiStreamPath

                fun connectLater(@ApiStreamPath streamPath: String) {}

                fun load() {
                    connectLater("<caret>")
                }
                """.trimIndent()
            )!!
        ).containsExactly("chat", "echo").inOrder()
    }

    fun testEndpointsKobwebRejectsAreNotSuggested() {
        addFileToProject(
            Platform.Jvm,
            "server/Outside.kt",
            // language=kotlin
            """
            package $TEST_GROUP.server

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext
            import com.varabyte.kobweb.api.stream.ApiStream

            // Outside the api package, without an absolute route override
            @Api
            suspend fun outside(ctx: ApiContext) {
            }

            val outsideStream = ApiStream { }
            """.trimIndent()
        )
        addFileToProject(
            Platform.Jvm,
            "api/Rejected.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext
            import com.varabyte.kobweb.api.stream.ApiStream

            @Api("a//b")
            suspend fun invalidOverride(ctx: ApiContext) {
            }

            // Kobweb doesn't currently allow dynamic segments in stream routes
            @Api("{id}")
            val dynamicStream = ApiStream { }

            private val privateStream = ApiStream { }

            object Streams {
                val nestedStream = ApiStream { }
            }

            // Not exactly an `ApiStream`
            val notAStream = "ApiStream"
            """.trimIndent()
        )

        assertThat(complete(produceFetch("<caret>"))!!).containsExactly("hello", "users/{user}").inOrder()
        assertThat(complete(produceStream("<caret>"))!!).containsExactly("chat", "echo").inOrder()
    }

    fun testEndpointsOutsideTheApiPackageCanUseAnAbsoluteRouteOverride() {
        addFileToProject(
            Platform.Jvm,
            "server/Outside.kt",
            // language=kotlin
            """
            package $TEST_GROUP.server

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext
            import com.varabyte.kobweb.api.stream.ApiStream

            @Api("/status")
            suspend fun status(ctx: ApiContext) {
            }

            @Api("/news")
            val newsStream = ApiStream { }
            """.trimIndent()
        )

        assertThat(complete(produceFetch("<caret>"))!!).containsExactly("hello", "status", "users/{user}").inOrder()
        assertThat(complete(produceStream("<caret>"))!!).containsExactly("chat", "echo", "news").inOrder()
    }

    fun testApiPackageComesFromTheBuildScript() {
        registerKobwebProject(apiPackage = ".server")
        addFileToProject(
            Platform.Jvm,
            "server/Status.kt",
            // language=kotlin
            """
            package $TEST_GROUP.server

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api
            suspend fun status(ctx: ApiContext) {
            }
            """.trimIndent()
        )

        // The endpoints under "api" are now outside the api package
        assertThat(complete(produceFetch("<caret>"))!!).containsExactly("status")
    }

    fun testBackendPackageMappingsAreUsed() {
        addFileToProject(
            Platform.Jvm,
            "api/users/PackageMapping.kt",
            // language=kotlin
            """
            @file:PackageMapping("people")

            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.PackageMapping
            """.trimIndent()
        )

        assertThat(complete(produceFetch("<caret>"))!!).containsExactly("hello", "people/{user}").inOrder()
    }

    fun testLibraryEndpointsAreSuggested() {
        val libsDir = FileUtil.createTempDirectory("kobweb-libs", null)
        Disposer.register(testRootDisposable) { FileUtil.delete(libsDir) }
        libsDir.resolve("kobweb-lib/$KOBWEB_METADATA_BACKEND").apply { parentFile.mkdirs() }.writeText(
            """
            {
                "apiMethods": [ { "fqn": "com.example.lib.api.auth.login", "route": "/auth/login" } ],
                "apiStreamProperties": [ { "fqn": "com.example.lib.api.presence", "route": "/presence" } ]
            }
            """.trimIndent()
        )
        PsiTestUtil.addLibrary(testRootDisposable, jvmModule, "kobweb-lib", libsDir.path, "kobweb-lib")

        assertThat(complete(produceFetch("<caret>"))!!).containsExactly("auth/login", "hello", "users/{user}").inOrder()
        assertThat(complete(produceStream("<caret>"))!!).containsExactly("chat", "echo", "presence").inOrder()
    }
}
