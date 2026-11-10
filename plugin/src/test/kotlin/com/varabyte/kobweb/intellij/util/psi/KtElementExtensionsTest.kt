@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.util.psi

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

class KtElementExtensionsTest : BasePlatformTestCase() {
    private fun replaceFirstImport(newFqName: String) {
        val file = myFixture.file as KtFile
        WriteCommandAction.runWriteCommandAction(project) {
            file.importDirectives.first().replaceImportedFqName(FqName(newFqName))
        }
    }

    fun testReplaceImportedFqName() {
        myFixture.configureByText(
            "test.kt",
            // language=kotlin
            """
                import a.b.Old
                import x.y.Other

                fun f() = Old() to Other()
            """.trimIndent()
        )

        replaceFirstImport("c.d.Old")

        myFixture.checkResult(
            """
                import c.d.Old
                import x.y.Other

                fun f() = Old() to Other()
            """.trimIndent()
        )
    }

    fun testReplaceImportedFqNameKeepsAlias() {
        myFixture.configureByText(
            "test.kt",
            // language=kotlin
            """
                import a.b.Old as Alias
                import x.y.Other

                fun f() = Alias() to Other()
            """.trimIndent()
        )

        replaceFirstImport("c.d.Old")

        // Kotlin's default import layout puts alias imports last
        myFixture.checkResult(
            """
                import x.y.Other
                import c.d.Old as Alias

                fun f() = Alias() to Other()
            """.trimIndent()
        )
    }

    fun testReplaceImportedFqNameKeepsStarImport() {
        myFixture.configureByText(
            "test.kt",
            // language=kotlin
            """
                import a.b.*
                import x.y.Other

                fun f() = Old() to Other()
            """.trimIndent()
        )

        replaceFirstImport("c.d")

        myFixture.checkResult(
            """
                import c.d.*
                import x.y.Other

                fun f() = Old() to Other()
            """.trimIndent()
        )
    }
}
