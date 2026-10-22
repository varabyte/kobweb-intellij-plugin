package com.varabyte.kobweb.intellij.inlay

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Document
import com.intellij.psi.SyntaxTraverser
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.truthish.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

/**
 * A recorded hint.
 *
 * @property isAboveLine If true, the hint is shown on its own line, above the line containing [offset]
 *   (i.e. [AboveLineIndentedPosition]). Otherwise, it's shown inline at [offset] (which, for an [EndOfLinePosition], is
 *   the end of its line).
 */
private class RecordedHint(val offset: Int, val text: String, val isAboveLine: Boolean)

private class RecordingSink(private val enabledOptions: Set<String>, private val document: Document) : InlayTreeSink {
    val hints = mutableListOf<RecordedHint>()

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
        val text = TextRecorder().apply(builder).text.toString()
        @Suppress("UnstableApiUsage")
        hints += when (position) {
            is InlineInlayPosition -> RecordedHint(position.offset, text, isAboveLine = false)
            is AboveLineIndentedPosition -> RecordedHint(position.offset, text, isAboveLine = true)
            is EndOfLinePosition -> RecordedHint(document.getLineEndOffset(position.line), text, isAboveLine = false)
        }
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

/**
 * A base class for testing inlay hints.
 *
 * Expected hints are written into the test code, using the same format as IntelliJ's own inlay hint tests:
 * `/*<# text #>*/` for an inline hint (or an end-of-line hint, written at the end of its line), and a line containing
 * just `/*<# block [text] #>*/` for a hint shown on its own line (above the line after it).
 */
abstract class KobwebInlayHintsTestBase : KobwebApplicationTestCase() {
    private val badgeRegex = Regex("""/\*<# (.*?) #>\*/""")
    private val aboveLineHintRegex = Regex("""(?m)^[ \t]*/\*<# block \[(.*?)] #>\*/\n""")
    protected fun badge(text: String) = """/*<# $text #>*/"""

    protected abstract fun produceInlayProvider(): InlayHintsProvider

    @OptIn(KaAllowAnalysisOnEdt::class)
    protected fun doTest(
        @Language("kotlin") code: String,
        enabledOptions: Set<String> = emptySet(),
        fileName: String = "InlayTest.kt",
    ) {
        val withoutBadges = code.replace(aboveLineHintRegex, "").replace(badgeRegex, "")

        val file = myFixture.configureByText(fileName, withoutBadges)
        myFixture.doHighlighting()

        val collector = produceInlayProvider()
            .createCollector(file, myFixture.editor) as? SharedBypassCollector
            ?: error("Could not create an inlay provider collector")

        val sink = RecordingSink(enabledOptions, myFixture.editor.document)
        allowAnalysisOnEdt {
            SyntaxTraverser.psiTraverser(file).forEach { element ->
                collector.collectFromElement(element, sink)
            }
        }

        val withBadges = StringBuilder(withoutBadges).apply {
            sink.hints
                .map { hint ->
                    if (hint.isAboveLine) {
                        val lineStart = withoutBadges.lastIndexOf('\n', hint.offset - 1) + 1
                        val indent = withoutBadges.substring(lineStart).takeWhile { it == ' ' || it == '\t' }
                        lineStart to "$indent/*<# block [${hint.text}] #>*/\n"
                    } else {
                        hint.offset to badge(hint.text)
                    }
                }
                .sortedByDescending { (offset, _) -> offset }
                .forEach { (offset, text) -> insert(offset, text) }
        }.toString()

        assertEquals(code, withBadges)
    }
}