package com.varabyte.kobweb.intellij.inlay

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.psi.SyntaxTraverser
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

private class RecordingSink(private val enabledOptions: Set<String>) : InlayTreeSink {
    val hints = mutableListOf<Pair<Int, String>>() // offset to text

    override fun whenOptionEnabled(optionId: String, block: () -> Unit) {
        if (optionId in enabledOptions) block()
    }

    override fun addPresentation(
        position: InlayPosition,
        payloads: List<InlayPayload>?,
        tooltip: String?,
        hintFormat: HintFormat,
        builder: PresentationTreeBuilder.() -> Unit,
    ) {
        val offset = (position as InlineInlayPosition).offset
        hints += offset to TextRecorder().apply(builder).text.toString()
    }
}

private class TextRecorder : PresentationTreeBuilder {
    val text = StringBuilder()
    override fun text(text: String, actionData: InlayActionData?) { this.text.append(text) }

    override fun list(builder: PresentationTreeBuilder.() -> Unit) {
        TODO("Not yet implemented")
    }
    override fun collapsibleList(
        state: CollapseState,
        expandedState: CollapsiblePresentationTreeBuilder.() -> Unit,
        collapsedState: CollapsiblePresentationTreeBuilder.() -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun clickHandlerScope(
        actionData: InlayActionData,
        builder: PresentationTreeBuilder.() -> Unit
    ) {
        TODO("Not yet implemented")
    }
}

abstract class KobwebInlayHintsTestBase : KobwebApplicationTestCase() {
    private val badgeRegex = Regex("""/\*<# (.*?) #>\*/""")
    protected fun badge(text: String) = """/*<# $text #>*/"""

    protected abstract fun produceInlayProvider(): InlayHintsProvider

    @OptIn(KaAllowAnalysisOnEdt::class)
    protected fun doTest(
        @Language("kotlin") code: String,
        enabledOptions: Set<String>
    ) {
        val withoutBadges = code.replace(badgeRegex, "")

        val file = myFixture.configureByText("InlayTest.kt", withoutBadges)
        myFixture.doHighlighting()

        val collector = produceInlayProvider()
            .createCollector(file, myFixture.editor) as? SharedBypassCollector
            ?: error("Could not create an inlay provider collector")

        val sink = RecordingSink(enabledOptions)
        allowAnalysisOnEdt {
            SyntaxTraverser.psiTraverser(file).forEach { element ->
                collector.collectFromElement(element, sink)
            }
        }

        val withBadges = StringBuilder(withoutBadges).apply {
            sink.hints.sortedByDescending { it.first }.forEach { (offset, hint) ->
                insert(offset, badge(hint))
            }
        }.toString()

        assertEquals(code, withBadges)
    }
}