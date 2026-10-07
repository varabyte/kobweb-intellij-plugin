package com.varabyte.kobweb.intellij.testutil

// WIP: Shared base class for tests that need a (fake) Kobweb project. Commented out because it could not be compiled
// or run when it was written (the sandbox had no access to JetBrains' download servers).
//
// To enable the WIP tests in this directory tree:
//
// 1. Add the platform test framework to plugin/build.gradle.kts:
//
//      import org.jetbrains.intellij.platform.gradle.TestFrameworkType
//      dependencies {
//          testImplementation("junit:junit:4.13.2")
//          intellijPlatform { testFramework(TestFrameworkType.Platform) }
//      }
//
//    and run tests in K2 mode: `tasks.test { systemProperty("idea.kotlin.plugin.use.k2", "true") }`
//
// 2. Solve "how do we make a light test project look like a Kobweb module?" Kobweb detection
//    (`PsiElement.findKobwebProject()`) requires:
//      a. `project.kobwebPluginState == INITIALIZED` (easy; just set it in setUp)
//      b. `module.toGradleModule()` to be non-null, which requires Gradle external-system data to be registered for
//         the test module (hard; needs a ProjectData/ModuleData DataNode imported via ProjectDataManager)
//      c. A `KobwebProject` pre-registered in `KobwebProjectCacheService` for that gradle module (easy; avoids
//         having to run the Gradle tooling model in tests)
//    Step (b) is the open question. Alternatives: introduce a tiny test seam in KobwebProjectUtils.kt, or register the
//    fake external-system data.
//
// 3. Un-comment this file and the sibling tests.
//
// import com.intellij.openapi.components.service
// import com.intellij.testFramework.fixtures.BasePlatformTestCase
// import com.varabyte.kobweb.intellij.model.KobwebProjectType
// import com.varabyte.kobweb.intellij.project.KobwebProject
// import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService
// import com.varabyte.kobweb.intellij.util.kobweb.KobwebPluginState
// import com.varabyte.kobweb.intellij.util.kobweb.kobwebPluginState
// import com.varabyte.kobweb.intellij.util.module.toGradleModule
//
// abstract class KobwebLightTestCase : BasePlatformTestCase() {
//     override fun setUp() {
//         super.setUp()
//         addKobwebStubs()
//         markModuleAsKobweb(KobwebProjectType.Application)
//     }
//
//     override fun tearDown() {
//         try {
//             project.service<KobwebProjectCacheService>().clear()
//             project.kobwebPluginState = KobwebPluginState.DISABLED
//         } finally {
//             super.tearDown()
//         }
//     }
//
//     protected fun markModuleAsKobweb(type: KobwebProjectType) {
//         project.kobwebPluginState = KobwebPluginState.INITIALIZED
//         val gradleModule = module.toGradleModule() ?: module // see step 2b above
//         project.service<KobwebProjectCacheService>()
//             .add(KobwebProject("test", type, KobwebProject.Source.Local(gradleModule)))
//     }
//
//     /** Minimal stand-ins for the parts of the Kobweb framework that the plugin looks up by ClassId / CallableId. */
//     private fun addKobwebStubs() {
//         myFixture.addFileToProject(
//             "stubs/Modifier.kt",
//             """
//             package com.varabyte.kobweb.compose.ui
//             interface Modifier { companion object : Modifier }
//             fun Modifier.fillMaxWidth(): Modifier = this
//             fun Modifier.color(value: String): Modifier = this
//             """.trimIndent()
//         )
//         myFixture.addFileToProject(
//             "stubs/Style.kt",
//             """
//             package com.varabyte.kobweb.silk.style
//             import com.varabyte.kobweb.compose.ui.Modifier
//             class CssStyle
//             class StyleScope { fun base(init: () -> Modifier) {} }
//             fun CssStyle(init: StyleScope.() -> Unit): CssStyle = CssStyle()
//             """.trimIndent()
//         )
//     }
// }
