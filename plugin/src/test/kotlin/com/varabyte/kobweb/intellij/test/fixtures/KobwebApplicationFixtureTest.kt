package com.varabyte.kobweb.intellij.test.fixtures

 import com.intellij.openapi.vfs.VfsUtilCore
 import com.intellij.openapi.vfs.VirtualFile
 import com.intellij.openapi.vfs.VirtualFileVisitor
 import com.varabyte.truthish.assertThat

class KobwebApplicationFixtureTest : KobwebApplicationTestCase() {
    fun testAllStubsAreSetUpCorrectly() {
        val stubsDir = myFixture.findFileInTempDir("src/stubs") ?: error("Could not find src/stubs directory")
        val stubsList = mutableListOf<VirtualFile>()
        VfsUtilCore.visitChildrenRecursively(stubsDir, object : VirtualFileVisitor<Void>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (!file.isDirectory && file.extension == "kt") {
                    stubsList.add(file)
                }
                return true
            }
        })

        assertThat(stubsList).isNotEmpty()
        myFixture.testHighlightingAllFiles(true, false, false, *stubsList.toTypedArray())
    }
}
