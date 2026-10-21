package com.varabyte.kobweb.intellij.inlay

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModChooseAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.test.util.choicesByName
import com.varabyte.kobweb.intellij.test.util.performAndExecute
import com.varabyte.kobweb.intellij.util.kobweb.layout.LayoutPathChooser
import com.varabyte.kobweb.intellij.util.kobweb.layout.addNoLayoutAnnotation
import com.varabyte.kobweb.intellij.util.kobweb.layout.findDefaultLayout
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.psi.KtNamedFunction

class KobwebAnnotationInlayHintsProviderTest : KobwebInlayHintsTestBase() {
    override fun produceInlayProvider() = KobwebAnnotationInlayHintsProvider()

    private fun doTest(@Language("kotlin") code: String) {
        doTest(code, enabledOptions = setOf(KobwebAnnotationInlayHintsProvider.DEFAULT_LAYOUT_OPTION))
    }

    override fun setUp() {
        super.setUp()

        myFixture.addFileToProject(
            "components/layouts/Layouts.kt",
            // language=kotlin
            """
            package $TEST_GROUP.components.layouts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.layout.Layout

            @Layout
            @Composable
            fun PageLayout(content: @Composable () -> Unit) {
                content()
            }

            @Layout
            @Composable
            fun BlogLayout(content: @Composable () -> Unit) {
                content()
            }
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "pages/DefaultLayout.kt",
            // language=kotlin
            """
            @file:Layout(".components.layouts.PageLayout")

            package $TEST_GROUP.pages

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "pages/blog/DefaultLayout.kt",
            // language=kotlin
            """
            @file:Layout(".components.layouts.BlogLayout")

            package $TEST_GROUP.pages.blog

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )
    }

    fun testPageShowsDefaultLayout() {
        doTest(
            """
            package $TEST_GROUP.pages.about

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            /*<# block [@Layout(".components.layouts.PageLayout")] #>*/
            @Composable
            fun AboutPage() {
            }
            """.trimIndent()
        )
    }

    fun testNoHintWhenOptionIsDisabled() {
        doTest(
            """
            package $TEST_GROUP.pages.about

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun AboutPage() {
            }
            """.trimIndent(),
            enabledOptions = emptySet(),
        )
    }

    fun testClosestDefaultLayoutWins() {
        doTest(
            """
            package $TEST_GROUP.pages.blog.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            /*<# block [@Layout(".components.layouts.BlogLayout")] #>*/
            @Composable
            fun FirstPost() {
            }
            """.trimIndent()
        )
    }

    fun testDefaultLayoutsMatchWholePackageNames() {
        // A default layout set in "pages.blog" shouldn't apply to "pages.blogging"
        doTest(
            """
            package $TEST_GROUP.pages.blogging

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            /*<# block [@Layout(".components.layouts.PageLayout")] #>*/
            @Composable
            fun BloggingTips() {
            }
            """.trimIndent()
        )
    }

    fun testHintIsShownInlineWhenPageAnnotationSharesItsLine() {
        doTest(
            """
            package $TEST_GROUP.pages.about

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page/*<# @Layout(".components.layouts.PageLayout") #>*/ @Composable
            fun AboutPage() {
            }
            """.trimIndent()
        )
    }

    fun testNoHintForPagesThatChooseTheirOwnLayout() {
        doTest(
            """
            package $TEST_GROUP.pages.about

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            @Layout(".components.layouts.BlogLayout")
            @Composable
            fun AboutPage() {
            }

            @Page
            @NoLayout
            @Composable
            fun ContactPage() {
            }
            """.trimIndent()
        )
    }

    fun testNoHintForFunctionsThatAreNotPages() {
        doTest(
            """
            package $TEST_GROUP.pages.about

            import androidx.compose.runtime.Composable

            @Composable
            fun AboutWidget() {
            }
            """.trimIndent()
        )
    }

    fun testDefaultLayoutsOutsidePagesPackageAreIgnored() {
        // Kobweb ignores these
        myFixture.addFileToProject(
            "other/DefaultLayout.kt",
            // language=kotlin
            """
            @file:Layout(".components.layouts.PageLayout")

            package $TEST_GROUP.other

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )

        doTest(
            """
            package $TEST_GROUP.other

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun OtherPage() {
            }
            """.trimIndent()
        )
    }

    private fun optOut(@Language("kotlin") code: String, @Language("kotlin") expected: String) {
        myFixture.configureByText("InlayTest.kt", code)
        val page = PsiTreeUtil.findChildOfType(myFixture.file, KtNamedFunction::class.java)!!
        WriteCommandAction.runWriteCommandAction(project) { page.addNoLayoutAnnotation() }

        myFixture.checkResult(expected)
        assertNull(page.findDefaultLayout())
    }

    fun testOptingOutAddsNoLayoutAfterPage() {
        optOut(
            """
            package $TEST_GROUP.pages.about

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun AboutPage() {
            }
            """.trimIndent(),
            """
            package $TEST_GROUP.pages.about

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            @NoLayout
            @Composable
            fun AboutPage() {
            }
            """.trimIndent(),
        )
    }

    fun testOptingOutFollowsCodeStyle() {
        // The IDE reformats the code we add, and Kotlin's default code style puts each function annotation on its own
        // line
        optOut(
            """
            package $TEST_GROUP.pages.about

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page @Composable
            fun AboutPage() {
            }
            """.trimIndent(),
            """
            package $TEST_GROUP.pages.about

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.NoLayout

            @Page
            @NoLayout
            @Composable
            fun AboutPage() {
            }
            """.trimIndent(),
        )
    }

    fun testChoosingDifferentLayoutAddsLayoutAfterPage() {
        myFixture.configureByText(
            "InlayTest.kt",
            """
            package $TEST_GROUP.pages.blog.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page

            @Page
            @Composable
            fun FirstPost() {
            }
            """.trimIndent()
        )
        val page = PsiTreeUtil.findChildOfType(myFixture.file, KtNamedFunction::class.java)!!

        val context = ActionContext.from(myFixture.editor, myFixture.file)
        val chooser = LayoutPathChooser.choose(page) as ModChooseAction
        val choices = chooser.choicesByName(context)
        // The page already uses `BlogLayout` by default, so it isn't offered
        assertThat(choices.keys).containsExactly(".components.layouts.PageLayout")

        choices.getValue(".components.layouts.PageLayout").performAndExecute(context)

        myFixture.checkResult(
            """
            package $TEST_GROUP.pages.blog.posts

            import androidx.compose.runtime.Composable
            import com.varabyte.kobweb.core.Page
            import com.varabyte.kobweb.core.layout.Layout

            @Page
            @Layout(".components.layouts.PageLayout")
            @Composable
            fun FirstPost() {
            }
            """.trimIndent()
        )
    }
}
