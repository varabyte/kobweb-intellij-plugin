@file:Suppress("FunctionName", "RedundantSuspendModifier")

package com.varabyte.kobweb.intellij.rename.route

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.PsiTestUtil
import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import com.varabyte.truthish.assertThat
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameDispatcherHandler
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

@OptIn(KaAllowAnalysisOnEdt::class)
class RouteRenameHandlerTest : KobwebFullStackTestCase() {
    private lateinit var aboutPage: KtNamedFunction
    private lateinit var helloApi: KtNamedFunction
    private lateinit var chatStream: KtProperty

    override fun setUp() {
        super.setUp()

        aboutPage = addFileToProject(
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
        ).findDescendantOfType<KtNamedFunction>()!!

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

        chatStream = addFileToProject(
            Platform.Jvm,
            "api/Chat.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.stream.ApiStream

            val chat = ApiStream { ctx -> }
            """.trimIndent()
        ).findDescendantOfType<KtProperty>()!!
    }

    /**
     * Returns the rename handler that the Rename action would use at the caret.
     *
     * This also checks that IntelliJ wouldn't ask the user to choose between several handlers first.
     */
    private fun renameHandlerAtCaret(): RenameHandler {
        val context = EditorUtil.getEditorDataContext(myFixture.editor)
        return allowAnalysisOnEdt {
            ReadAction.compute<RenameHandler, Throwable> {
                val handler = RenameHandlerRegistry.getInstance().getRenameHandlers(context).single()
                // The Kotlin plugin's handler delegates to whichever of its own handlers is available
                (handler as? KotlinRenameDispatcherHandler)?.getRenameHandler(context) ?: handler
            }
        }
    }

    /** Renames at the caret, the same way the Rename action does, and returns the hint shown instead (if any). */
    private fun renameAtCaret(newName: String): String? {
        return try {
            allowAnalysisOnEdt { myFixture.renameElementAtCaretUsingHandler(newName) }
            null
        } catch (e: RefactoringErrorHintException) {
            e.message
        }
    }

    fun testRenamingFromPageRouteIsRefused() {
        configureByText(
            Platform.Js,
            "Nav.kt",
            // language=kotlin
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Nav() {
                Link("/ab<caret>out")
            }
            """.trimIndent()
        )

        assertThat(renameHandlerAtCaret()::class).isEqualTo(RouteRenameHandler::class)
        assertThat(renameAtCaret("ContactPage")).isEqualTo(
            "This route comes from where `AboutPage` is defined, not from its name, so rename or move its file (`About.kt`) instead."
        )
        assertThat(aboutPage.name).isEqualTo("AboutPage")
    }

    fun testRenamingFromApiPathIsRefused() {
        configureByText(
            Platform.Js,
            "Fetch.kt",
            // language=kotlin
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.browser.api
            import kotlinx.browser.window

            suspend fun fetch() {
                window.api.get("hel<caret>lo")
            }
            """.trimIndent()
        )

        assertThat(renameHandlerAtCaret()::class).isEqualTo(RouteRenameHandler::class)
        assertThat(renameAtCaret("greet")).isNotNull()
        assertThat(helloApi.name).isEqualTo("hello")
    }

    fun testRenamingFromStreamPathIsRefused() {
        configureByText(
            Platform.Js,
            "Connect.kt",
            // language=kotlin
            """
            package $TEST_GROUP.components

            import com.varabyte.kobweb.streams.ApiStream

            fun connect() = ApiStream("ch<caret>at")
            """.trimIndent()
        )

        assertThat(renameHandlerAtCaret()::class).isEqualTo(RouteRenameHandler::class)
        assertThat(renameAtCaret("chatty")).isEqualTo(
            "This route comes from where `chat` is defined, not from its name, so rename or move its file (`Chat.kt`) instead."
        )
        assertThat(chatStream.name).isEqualTo("chat")
    }

    fun testRenamingEndpointByItsOwnNameIsUnaffected() {
        // Renaming an endpoint's declaration directly (as opposed to from a path that leads to it) is a normal Kotlin
        // rename, which we shouldn't interfere with
        configureByText(
            Platform.Jvm,
            "api/Echo.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.stream.ApiStream

            val ec<caret>ho = ApiStream { ctx -> ctx.stream.send(ctx.text) }
            """.trimIndent()
        )

        assertThat(renameHandlerAtCaret()::class).isNotEqualTo(RouteRenameHandler::class)
    }

    fun testRenamingFromPublicFilePathIsUnaffected() {
        // Files in `resources/public/` are served by Kobweb
        val resourcesRoot = myFixture.tempDirFixture.findOrCreateDir("resources")
        PsiTestUtil.addSourceRoot(jsModule, resourcesRoot, JavaResourceRootType.RESOURCE)
        Disposer.register(testRootDisposable) { PsiTestUtil.removeSourceRoot(jsModule, resourcesRoot) }
        addFileToProject(Platform.Js, "resources/public/images/cat.png", "")

        configureByText(
            Platform.Js,
            "Cat.kt",
            // language=kotlin
            """
            package $TEST_GROUP.components

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.silk.components.navigation.Link

            @Composable
            fun Cat() {
                Link("/images/c<caret>at.png")
            }
            """.trimIndent()
        )

        assertThat(renameHandlerAtCaret()::class).isNotEqualTo(RouteRenameHandler::class)
    }
}
