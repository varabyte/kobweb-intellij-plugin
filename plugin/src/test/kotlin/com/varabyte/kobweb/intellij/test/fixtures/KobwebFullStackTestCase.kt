package com.varabyte.kobweb.intellij.test.fixtures

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.nio.file.Paths

/**
 * A test case for features that span a Kobweb application's frontend and backend, e.g. API routes.
 *
 * Like the `jsMain` and `jvmMain` source sets of a Kobweb site, the test's main module ([jsModule]) is Kotlin/JS, and a
 * second module ([jvmModule]) is a plain JVM module with Kobweb's backend libraries. Neither module depends on the
 * other, but both belong to the same Kobweb project.
 *
 * Add files to them with [addJsFileToProject] and [addJvmFileToProject]. (`myFixture` adds files to the JS module.)
 */
abstract class KobwebFullStackTestCase : KobwebApplicationTestCase() {
    companion object {
        private const val JVM_MODULE_NAME = "jvmMain"

        private val KOBWEB_FULL_STACK = object : KobwebJsProjectDescriptor() {
            override fun setUpProject(project: Project, handler: SetupHandler) {
                super.setUpProject(project, handler)

                WriteAction.run<Throwable> {
                    val mainModule = ModuleManager.getInstance(project).findModuleByName(TEST_MODULE_NAME)!!
                    val jvmModule = createModule(project, Paths.get(FileUtil.getTempDirectory(), "$JVM_MODULE_NAME.iml"))
                    val sourceRoot = createSourceRoot(jvmModule, JVM_MODULE_NAME)
                    ModuleRootModificationUtil.updateModel(jvmModule) { model ->
                        model.sdk = ModuleRootManager.getInstance(mainModule).sdk
                        model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_11
                        model.addContentEntry(sourceRoot).addSourceFolder(sourceRoot, JavaSourceRootType.SOURCE)
                    }
                    ModuleRootModificationUtil.addModuleLibrary(
                        jvmModule,
                        "kobweb-jvm",
                        filesFromProperty("kobweb.test.jvm.jars").map { VfsUtil.getUrlForLibraryRoot(it.toPath()) },
                        filesFromProperty("kobweb.test.jvm.sources").map { VfsUtil.getUrlForLibraryRoot(it.toPath()) },
                    )
                }
                IndexingTestUtil.waitUntilIndexesAreReady(project)
            }
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KOBWEB_FULL_STACK
    }

    /** The module standing in for a Kobweb site's `jsMain` source set (i.e. the test's main module). */
    protected val jsModule: Module get() = module

    /** The module standing in for a Kobweb site's `jvmMain` source set. */
    protected val jvmModule: Module get() = ModuleManager.getInstance(project).findModuleByName(JVM_MODULE_NAME)!!

    private val jvmSourceRoot: VirtualFile get() = ModuleRootManager.getInstance(jvmModule).sourceRoots.single()

    override val kobwebModules get() = listOf(jsModule, jvmModule)

    /**
     * Adds the target file to the [jsModule].
     *
     * Note: This is exactly the same as calling `myFixture.addFileToProject` but should be preferred for readability.
     */
    protected fun addJsFileToProject(relativePath: String, text: String): PsiFile {
        return myFixture.addFileToProject(relativePath, text)
    }

    /** Adds the target file to the [jvmModule]. */
    protected fun addJvmFileToProject(relativePath: String, text: String): PsiFile {
        val file = VfsTestUtil.createFile(jvmSourceRoot, relativePath, text)
        return PsiManager.getInstance(project).findFile(file)!!
    }

    override fun tearDown() {
        try {
            // The light test framework only clears the main module's source root between tests
            WriteAction.runAndWait<Throwable> { jvmSourceRoot.children.forEach { it.delete(this) } }
        } finally {
            super.tearDown()
        }
    }
}
