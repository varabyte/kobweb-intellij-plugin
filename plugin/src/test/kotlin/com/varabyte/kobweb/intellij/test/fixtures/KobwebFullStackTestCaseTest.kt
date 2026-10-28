@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.test.fixtures

import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.truthish.assertThat
import org.jetbrains.kotlin.idea.base.util.module

/** Sanity checks that the fixture sets up modules like a real Kobweb site's `jsMain` and `jvmMain`. */
class KobwebFullStackTestCaseTest : KobwebFullStackTestCase() {
    fun testJvmModuleUsesKobwebBackendLibraries() {
        val file = addJvmFileToProject(
            "com/example/api/Hello.kt",
            // language=kotlin
            """
            package com.example.api

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api
            suspend fun hello(ctx: ApiContext) {
                ctx.res.status = 200
            }
            """.trimIndent()
        )
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.checkHighlighting()
    }

    fun testJsModuleCantSeeJvmCode() {
        addJvmFileToProject(
            "com/example/Backend.kt",
            // language=kotlin
            """
            package com.example

            fun backendOnly() = Unit
            """.trimIndent()
        )
        myFixture.configureByText(
            "Frontend.kt",
            // language=kotlin
            """
            package com.example

            import com.varabyte.kobweb.<error descr="[UNRESOLVED_IMPORT] Unresolved reference 'api'.">api</error>.Api

            fun frontend() = <error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'backendOnly'.">backendOnly</error>()
            """.trimIndent()
        )
        myFixture.checkHighlighting()
    }

    fun testBothModulesBelongToSameKobwebProject() {
        val jvmFile = addJvmFileToProject("com/example/Backend.kt", "package com.example")
        val jsFile = addJsFileToProject("com/example/Frontend.kt", "package com.example")

        assertThat(jsFile.module).isEqualTo(jsModule)
        assertThat(jvmFile.module).isEqualTo(jvmModule)
        assertThat(jvmFile.findKobwebProject()).isNotNull()
        assertThat(jvmFile.findKobwebProject()).isEqualTo(jsFile.findKobwebProject())
    }
}
