@file:Suppress("FunctionName", "RedundantSuspendModifier", "ReplaceGetOrSet")

package com.varabyte.kobweb.intellij.references.api

import com.intellij.psi.util.parentOfType
import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

class ApiPathReferenceTest : KobwebFullStackTestCase() {
    private lateinit var helloApi: KtNamedFunction

    override fun setUp() {
        super.setUp()

        helloApi = addFileToProject(
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
        ).findDescendantOfType<KtNamedFunction>()!!

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
            "api/users/Me.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api
            suspend fun me(ctx: ApiContext) {
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

    @Language("kotlin")
    private fun produceFetch(path: String) = """
        package $TEST_GROUP.components

        import com.varabyte.kobweb.browser.api
        import kotlinx.browser.window

        suspend fun fetch(id: String) {
            window.api.get("$path")
        }
    """.trimIndent()

    @Language("kotlin")
    private fun produceStream(path: String) = """
        package $TEST_GROUP.components

        import com.varabyte.kobweb.streams.ApiStream

        fun connect(room: String) = ApiStream("$path")
    """.trimIndent()

    /**
     * Returns the name of the API endpoint that the string at the caret leads to, if any.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun resolveApiPathAtCaret(@Language("kotlin") code: String): String? {
        configureByText(Platform.Js, "Fetch.kt", code)
        val stringExpr = myFixture.file.findElementAt(myFixture.caretOffset)!!
            .parentOfType<KtStringTemplateExpression>(withSelf = true)!!
        val reference = stringExpr.references.filterIsInstance<ApiPathReference>().singleOrNull() ?: return null
        return allowAnalysisOnEdt { (reference.resolve() as? KtCallableDeclaration)?.name }
    }

    fun testApiPathLeadsToApiFunction() {
        assertThat(resolveApiPathAtCaret(produceFetch("hel<caret>lo"))).isEqualTo("hello")
    }

    fun testLeadingSlashMakesNoDifference() {
        assertThat(resolveApiPathAtCaret(produceFetch("/hel<caret>lo"))).isEqualTo("hello")
        assertThat(resolveApiPathAtCaret(produceStream("/ch<caret>at"))).isEqualTo("chat")
    }

    fun testQueryParametersAreIgnored() {
        assertThat(resolveApiPathAtCaret(produceFetch("hel<caret>lo?name=bob"))).isEqualTo("hello")
    }

    fun testDynamicSegmentsMatchAnySegment() {
        assertThat(resolveApiPathAtCaret(produceFetch("users/b<caret>ob"))).isEqualTo("user")
    }

    fun testStaticSegmentsWinOverDynamicOnes() {
        assertThat(resolveApiPathAtCaret(produceFetch("users/m<caret>e"))).isEqualTo("me")
    }

    fun testSegmentsNotKnownUntilRuntimeOnlyMatchDynamicSegments() {
        assertThat(resolveApiPathAtCaret(produceFetch($$"us<caret>ers/$id"))).isEqualTo("user")
    }

    fun testUnknownApiPathsDontResolve() {
        assertThat(resolveApiPathAtCaret(produceFetch("good<caret>bye"))).isNull()
        assertThat(resolveApiPathAtCaret(produceFetch("us<caret>ers"))).isNull()
    }

    fun testStreamPathLeadsToStream() {
        assertThat(resolveApiPathAtCaret(produceStream("ch<caret>at"))).isEqualTo("chat")
    }

    fun testStreamPathsMustMatchExactly() {
        assertThat(resolveApiPathAtCaret(produceStream("ch<caret>a"))).isNull()
        assertThat(resolveApiPathAtCaret(produceStream($$"ch<caret>at/$room"))).isNull()
    }

    fun testApiPathsAndStreamPathsDontMix() {
        assertThat(resolveApiPathAtCaret(produceFetch("ch<caret>at"))).isNull()
        assertThat(resolveApiPathAtCaret(produceStream("hel<caret>lo"))).isNull()
    }

    fun testStringsNotUsedAsApiPathsDontResolve() {
        // Named like `ApiFetcher.get`, but doesn't take an API path
        assertThat(
            resolveApiPathAtCaret(
                """
                package $TEST_GROUP.components

                fun lookUp(map: Map<String, Int>) = map.get("hel<caret>lo")
                """.trimIndent()
            )
        ).isNull()
    }

    fun testRenamingApiFunctionLeavesApiPathsAlone() {
        configureByText(Platform.Js, "Fetch.kt", produceFetch("hello"))

        myFixture.renameElement(helloApi, "greet")

        myFixture.checkResult(produceFetch("hello"))
    }
}
