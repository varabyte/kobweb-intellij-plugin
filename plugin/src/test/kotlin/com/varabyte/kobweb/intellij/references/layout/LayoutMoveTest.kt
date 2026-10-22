package com.varabyte.kobweb.intellij.references.layout

import com.intellij.psi.PsiDirectory
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveOperationDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationsRefactoringProcessor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class LayoutMoveTest : KobwebApplicationTestCase() {
    @Language("kotlin")
    private fun producePageUsingLayout(layoutPath: String) = """
        package $TEST_GROUP.pages

        import androidx.compose.runtime.Composable
        import com.varabyte.kobweb.core.Page
        import com.varabyte.kobweb.core.layout.Layout

        @Page
        @Layout("$layoutPath")
        @Composable
        fun HomePage() {
        }
    """.trimIndent()

    // Note: All files live in directories that match their packages, as Kotlin's Move refactoring reports a conflict
    // otherwise

    private fun addLayoutFile() = myFixture.addFileToProject(
        "${TEST_GROUP.replace('.', '/')}/components/layouts/Layouts.kt",
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
        """.trimIndent()
    ) as KtFile

    /**
     * Returns the directory for [packageName], creating it (with a placeholder file) if necessary.
     */
    private fun findOrCreateDirectory(packageName: String): PsiDirectory {
        return myFixture.addFileToProject("${packageName.replace('.', '/')}/Placeholder.kt", "package $packageName")
            .containingDirectory
    }

    /**
     * Moves the `PageLayout` function into a file in [packageName], using Kotlin's Move refactoring (e.g. F6).
     */
    private fun moveLayout(packageName: String) {
        val layout = PsiTreeUtil.findChildOfType(addLayoutFile(), KtNamedFunction::class.java)!!
        val descriptor = K2MoveOperationDescriptor.Declarations(
            project,
            listOf(layout),
            findOrCreateDirectory(packageName),
            fileName = "Layouts.kt",
            pkgName = FqName(packageName),
            searchForText = false,
            searchReferences = true,
            searchInComments = false,
            mppDeclarations = false,
            dirStructureMatchesPkg = true,
            isMoveToExplicitPackage = true,
        )
        K2MoveDeclarationsRefactoringProcessor(descriptor).run()
    }

    /**
     * Moves the file containing the `PageLayout` function into [packageName], using Kotlin's Move refactoring for
     * files (e.g. dragging the file into another package).
     */
    private fun moveLayoutFile(packageName: String) {
        val layoutFile = addLayoutFile()
        val descriptor = K2MoveOperationDescriptor.Files(
            project,
            listOf(
                K2MoveDescriptor.Files(
                    project,
                    K2MoveSourceDescriptor.FileSource(listOf(layoutFile)),
                    K2MoveTargetDescriptor.Directory(FqName(packageName), findOrCreateDirectory(packageName)),
                )
            ),
            searchForText = false,
            searchInComments = false,
            searchReferences = true,
            dirStructureMatchesPkg = true,
            isMoveToExplicitPackage = true,
        )
        descriptor.refactoringProcessor().run()
    }

    /**
     * Checks that moving the layout into [targetPackage] updates a page's `@Layout` path from [path] to [expectedPath].
     */
    private fun doTest(path: String, targetPackage: String, expectedPath: String) {
        val page = myFixture.addFileToProject(
            "${TEST_GROUP.replace('.', '/')}/pages/HomePage.kt",
            producePageUsingLayout(path),
        )
        myFixture.configureFromExistingVirtualFile(page.virtualFile)

        moveLayout(targetPackage)

        myFixture.checkResult(producePageUsingLayout(expectedPath))
    }

    fun testMovingLayoutUpdatesRelativePath() {
        doTest(
            path = ".components.layouts.PageLayout",
            targetPackage = "$TEST_GROUP.components.site",
            expectedPath = ".components.site.PageLayout",
        )
    }

    fun testMovingLayoutUpdatesFullyQualifiedPath() {
        doTest(
            path = "$TEST_GROUP.components.layouts.PageLayout",
            targetPackage = "$TEST_GROUP.components.site",
            expectedPath = "$TEST_GROUP.components.site.PageLayout",
        )
    }

    fun testMovingLayoutOutsideGroupMakesRelativePathFullyQualified() {
        // A relative path can't point outside the group
        doTest(
            path = ".components.layouts.PageLayout",
            targetPackage = "org.other.layouts",
            expectedPath = "org.other.layouts.PageLayout",
        )
    }

    fun testMovingLayoutUpdatesDefaultLayoutPath() {
        val defaultLayout = myFixture.addFileToProject(
            "${TEST_GROUP.replace('.', '/')}/pages/DefaultLayout.kt",
            // language=kotlin
            """
            @file:Layout(".components.layouts.PageLayout")

            package $TEST_GROUP.pages

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(defaultLayout.virtualFile)

        moveLayout("$TEST_GROUP.components.site")

        myFixture.checkResult(
            // language=kotlin
            """
            @file:Layout(".components.site.PageLayout")

            package $TEST_GROUP.pages

            import com.varabyte.kobweb.core.layout.Layout
            """.trimIndent()
        )
    }

    fun testMovingLayoutFileUpdatesPath() {
        val page = myFixture.addFileToProject(
            "${TEST_GROUP.replace('.', '/')}/pages/HomePage.kt",
            producePageUsingLayout(".components.layouts.PageLayout"),
        )
        myFixture.configureFromExistingVirtualFile(page.virtualFile)

        moveLayoutFile("$TEST_GROUP.components.site")

        myFixture.checkResult(producePageUsingLayout(".components.site.PageLayout"))
    }

    fun testMovingLayoutDirectoryUpdatesPath() {
        val page = myFixture.addFileToProject(
            "${TEST_GROUP.replace('.', '/')}/pages/HomePage.kt",
            producePageUsingLayout(".components.layouts.PageLayout"),
        )
        myFixture.configureFromExistingVirtualFile(page.virtualFile)

        // Move the whole `layouts` directory into the `site` package
        val layoutsDir = addLayoutFile().containingDirectory!!
        val descriptor = K2MoveOperationDescriptor.Files(
            project,
            listOf(
                K2MoveDescriptor.Files(
                    project,
                    K2MoveSourceDescriptor.FileSource(listOf(layoutsDir)),
                    K2MoveTargetDescriptor.Directory(
                        FqName("$TEST_GROUP.site"),
                        findOrCreateDirectory("$TEST_GROUP.site"),
                    ),
                )
            ),
            searchForText = false,
            searchInComments = false,
            searchReferences = true,
            dirStructureMatchesPkg = true,
            isMoveToExplicitPackage = true,
        )
        descriptor.refactoringProcessor().run()

        myFixture.checkResult(producePageUsingLayout(".site.layouts.PageLayout"))
    }
}
