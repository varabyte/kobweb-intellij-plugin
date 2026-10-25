@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.services.project

import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat

class KobwebProjectCacheServiceTest : KobwebApplicationTestCase() {
    fun testFoldersCanBeRenamed() {
        // Directories don't have a containing file, which used to cause `KobwebProjectCacheService` to crash
        val imagesFolder = myFixture.addFileToProject("resources/public/images/logo.png", "").containingDirectory

        myFixture.renameElement(imagesFolder, "img")

        assertThat(imagesFolder.name).isEqualTo("img")
    }
}
