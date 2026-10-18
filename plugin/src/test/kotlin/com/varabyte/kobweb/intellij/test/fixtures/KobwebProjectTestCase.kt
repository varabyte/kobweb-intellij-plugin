package com.varabyte.kobweb.intellij.test.fixtures

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.util.PathUtil
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.model.WorkerBlock
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.project.KobwebProject.Source.Local
import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService
import com.varabyte.kobweb.intellij.util.kobweb.KobwebPluginState
import com.varabyte.kobweb.intellij.util.kobweb.kobwebPluginState
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalSourceModuleStateModificationEvent
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.platform.js.JsPlatforms
import java.io.File

/**
 * A base test case for a single Kobweb module, which has access to the real Kobweb framework.
 *
 * Subclasses determine what type of Kobweb project this module is treated as.
 */
abstract class KobwebProjectTestCase(private val projectType: KobwebProjectType) : BasePlatformTestCase() {
    companion object {
        /**
         * Returns the files listed in a path-separated system property, which the Gradle test task sets.
         *
         * See `kobwebTestLibs` in plugin/build.gradle.kts.
         */
        fun filesFromProperty(name: String): List<File> {
            val value = System.getProperty(name) ?: error("System property \"$name\" not set. Should have been configured by Gradle.")
            return value.split(File.pathSeparator).filter { it.isNotBlank() }.map(::File)
        }

        // Provides the real Kobweb framework (with its dependencies, including the Kotlin/JS stdlib)
        private val KOBWEB_WITH_JDK_11 = object : DefaultLightProjectDescriptor() {
            override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk11()

            /**
             * Configure the module like a real Kobweb JS module: JS stdlib with the Compose compiler plugin enabled.
             */
            private fun configureKotlinJsFacet(module: Module) {
                val composePluginJar = PathUtil.getJarPathForClass(
                    Class.forName("androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar")
                )

                val facetManager = FacetManager.getInstance(module)
                val facet = facetManager.createFacet(KotlinFacetType.INSTANCE, KotlinFacetType.NAME, null)
                facet.configuration.settings.targetPlatform = JsPlatforms.defaultJsPlatform
                facet.configuration.settings.compilerArguments = K2JSCompilerArguments().apply {
                    pluginClasspaths = arrayOf(composePluginJar)
                }
                facetManager.createModifiableModel().apply {
                    addFacet(facet)
                    commit()
                }
            }

            override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
                super.configureModule(module, model, contentEntry)
                model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_11

                // Our module is Kotlin/JS (see `configureKotlinJsFacet`), which ignores libraries not marked as JS
                val libraryTableModel = model.moduleLibraryTable.modifiableModel
                val library = libraryTableModel.createLibrary("kobweb", KotlinJavaScriptLibraryKind)
                libraryTableModel.commit()
                library.modifiableModel.apply {
                    filesFromProperty("kobweb.test.klibs").forEach {
                        addRoot(VfsUtil.getUrlForLibraryRoot(it.toPath()), OrderRootType.CLASSES)
                    }
                    filesFromProperty("kobweb.test.sources").forEach {
                        addRoot(VfsUtil.getUrlForLibraryRoot(it.toPath()), OrderRootType.SOURCES)
                    }
                    commit()
                }

                configureKotlinJsFacet(module)
            }
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KOBWEB_WITH_JDK_11
    }

    /**
     * Register the Kobweb project that this test's module represents, replacing any previous registration.
     */
    protected fun registerKobwebProject(
        worker: WorkerBlock? = if (projectType == KobwebProjectType.Worker) WorkerBlock() else null,
    ) {
        if (worker != null && projectType != KobwebProjectType.Worker) {
            error("Cannot set worker values for a non-worker project")
        }

        project.service<KobwebProjectCacheService>()
            .add(KobwebProject("test-shell", projectType, Local(this.module), worker))

        // Kotlin analysis may have cached results based on the previous registration (e.g. from an earlier test)
        WriteAction.run<Throwable> { project.publishGlobalSourceModuleStateModificationEvent() }
    }

    override fun setUp() {
        super.setUp()
        this.project.kobwebPluginState = KobwebPluginState.INITIALIZED
        registerKobwebProject()
    }

    override fun tearDown() {
        try {
            project.service<KobwebProjectCacheService>().clear()
            project.kobwebPluginState = KobwebPluginState.DISABLED
        } finally {
            super.tearDown()
        }
    }
}
