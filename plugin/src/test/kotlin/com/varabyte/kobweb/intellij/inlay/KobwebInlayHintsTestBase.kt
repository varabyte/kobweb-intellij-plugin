@file:Suppress("JetBrainsInternalApiUsage") // Test only

package com.varabyte.kobweb.intellij.inlay

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderExtensionBean
import com.intellij.codeInsight.hints.declarative.InlayProviderPassInfo
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPass
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.codeInsight.hints.declarative.impl.util.DeclarativeHintsDumpUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import org.intellij.lang.annotations.Language

/**
 * A base class for testing inlay hints.
 *
 * Expected hints are written into the test code, using the same format as IntelliJ's own inlay hint tests:
 * `/*<# text #>*/` for an inline hint (or an end-of-line hint, written at the end of its line), and a line containing
 * just `/*<# block [text] #>*/` for a hint shown on its own line (above the line after it).
 *
 * Hints are collected and rendered by IntelliJ's real inlay hints pass (the same way IntelliJ's own
 * `DeclarativeInlayHintsProviderTestCase` does it, which we can't extend as it uses a different fixture), so tests
 * see what users would, e.g. including IntelliJ truncating long inline hints.
 */
abstract class KobwebInlayHintsTestBase : KobwebApplicationTestCase() {
    protected fun badge(text: String) = """/*<# $text #>*/"""

    protected abstract fun produceInlayProvider(): InlayHintsProvider

    protected fun doTest(
        @Language("kotlin") code: String,
        enabledOptions: Set<String> = emptySet(),
        fileName: String = "InlayTest.kt",
    ) {
        val provider = produceInlayProvider()

        // The pass needs the provider's ID and a value for every one of its options, which come from its registration
        val registration = InlayHintsProviderExtensionBean.EP.extensionList
            .single { it.implementationClass == provider.javaClass.name }
        val options = registration.options.associate { option -> option.optionId!! to (option.optionId in enabledOptions) }
        val unknownOptions = enabledOptions - options.keys
        require(unknownOptions.isEmpty()) { "Unknown options for ${registration.requiredProviderId()}: $unknownOptions" }

        val sourceText = InlayDumpUtil.removeInlays(code)
        myFixture.configureByText(fileName, sourceText)

        val passInfo = InlayProviderPassInfo(provider, registration.requiredProviderId(), options)
        val pass = ActionUtil.underModalProgress(project, "") {
            DeclarativeInlayHintsPass(myFixture.file, myFixture.editor, listOf(passInfo), isPreview = false)
        }
        ActionUtil.underModalProgress(project, "") { pass.doCollectInformation(EmptyProgressIndicator()) }
        pass.applyInformationToEditor()

        val withBadges = DeclarativeHintsDumpUtil.dumpHints(sourceText, myFixture.editor) { presentationList ->
            presentationList.getEntries().joinToString("") { (it as TextInlayPresentationEntry).text }
        }
        assertEquals(code, withBadges)
    }
}
