package com.varabyte.kobweb.intellij.inlay

import com.intellij.codeInsight.hints.declarative.AboveLineIndentedPosition
import com.intellij.codeInsight.hints.declarative.HintColorKind
import com.intellij.codeInsight.hints.declarative.HintFontSize
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.HintMarginPadding
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayPayload
import com.intellij.codeInsight.hints.declarative.InlayPosition
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionNavigationHandler
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.codeInsight.hints.settings.showInlaySettings
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.BlockInlayPriority
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.LayoutPathChooser
import com.varabyte.kobweb.intellij.util.kobweb.layout.PAGE_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.addNoLayoutAnnotation
import com.varabyte.kobweb.intellij.util.kobweb.layout.displayPath
import com.varabyte.kobweb.intellij.util.kobweb.layout.findDefaultLayout
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction

// Matches how IntelliJ shows inferred annotations in Java code
private val HINT_FORMAT = HintFormat(
    HintColorKind.Default,
    HintFontSize.ABitSmallerThanInEditor,
    HintMarginPadding.OnlyPadding,
)

// The payload key for the page a default layout hint belongs to, which actions in the hint's menu act on
private const val PAGE_PAYLOAD = "page"

private fun PsiElement.toNavigationAction(): InlayActionData {
    return InlayActionData(
        PsiPointerInlayActionPayload(SmartPointerManager.createPointer(this)),
        PsiPointerInlayActionNavigationHandler.HANDLER_ID,
    )
}

/**
 * Where to show the default layout of a page tagged with [pageAnnotation].
 *
 * Kobweb's docs always put `@Layout` right after `@Page`, so we show the hint on its own line after it. If the
 * annotation shares its line with the code after it (e.g. `@Page @Composable`), there's no line to put it on, so we
 * show the hint inline instead.
 */
private fun findHintPosition(pageAnnotation: KtAnnotationEntry): InlayPosition {
    // Find the code after the annotation, noting whether it starts on a new line
    var startsNewLine = false
    var next = PsiTreeUtil.nextLeaf(pageAnnotation)
    while (next is PsiWhiteSpace || next is PsiComment) {
        if (next.textContains('\n')) startsNewLine = true
        next = PsiTreeUtil.nextLeaf(next)
    }

    return if (next != null && startsNewLine) {
        @Suppress("UnstableApiUsage")
        AboveLineIndentedPosition(next.textRange.startOffset, BlockInlayPriority.ANNOTATIONS)
    } else {
        InlineInlayPosition(pageAnnotation.textRange.endOffset, relatedToPrevious = true)
    }
}

/**
 * Shows Kobweb annotations that apply to code without being written there, like IntelliJ does for inferred
 * annotations in Java code.
 *
 * For example, a page that uses a default layout (set by a `@file:Layout` annotation) shows that layout as if the page
 * were annotated with it.
 */
class KobwebAnnotationInlayHintsProvider : InlayHintsProvider {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        if (!file.isInWritableKobwebProject()) return null

        return object : SharedBypassCollector {
            override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
                val page = element as? KtNamedFunction ?: return
                sink.whenOptionEnabled(DEFAULT_LAYOUT_OPTION) {
                    val defaultLayout = page.findDefaultLayout() ?: return@whenOptionEnabled
                    val pageAnnotation = page.annotationEntries.first { it.isAnnotationOfType(PAGE_FQN) }
                    val pagePayload = PsiPointerInlayActionPayload(SmartPointerManager.createPointer(page))

                    sink.addPresentation(
                        findHintPosition(pageAnnotation),
                        payloads = listOf(InlayPayload(PAGE_PAYLOAD, pagePayload)),
                        tooltip =
                            buildString {
                                append(UxGlobals.textBundle.message(
                                    "kobweb.inlay.hints.annotations.default.layout.tooltip",
                                    defaultLayout.displayPath,
                                ))
                                append("<br><br>")
                                append(UxGlobals.textBundle.message(
                                    "kobweb.inlay.hints.annotations.usage.tooltip",
                                    if (SystemInfo.isMac) MacKeymapUtil.COMMAND else "Ctrl"
                                ))
                            },
                        hintFormat = HINT_FORMAT,
                    ) {
                        text("@${LAYOUT_FQN.shortName()}", defaultLayout.annotation.toNavigationAction())
                        text("(\"")
                        text(defaultLayout.path, defaultLayout.layout.function?.toNavigationAction())
                        text("\")")
                    }
                }
            }
        }
    }

    companion object {
        const val PROVIDER_ID = "kobweb.annotation.hints"
        const val DEFAULT_LAYOUT_OPTION = "kobweb.annotation.hints.default.layout"
    }
}

/**
 * The page a default layout hint belongs to, if this event comes from that hint's menu.
 */
private val AnActionEvent.defaultLayoutHintPage: KtNamedFunction?
    get() {
        if (getData(InlayHintsProvider.PROVIDER_ID) != KobwebAnnotationInlayHintsProvider.PROVIDER_ID) return null
        val payload = getData(InlayHintsProvider.INLAY_PAYLOADS)?.get(PAGE_PAYLOAD) as? PsiPointerInlayActionPayload
        return payload?.pointer?.element as? KtNamedFunction
    }

/**
 * An action in the menu of a default layout hint, which gives its page a different layout, chosen from a popup.
 */
class ChooseLayoutForPageAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.defaultLayoutHintPage != null && e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val page = e.defaultLayoutHintPage ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val context = ActionContext.from(editor, page.containingFile)
        // Executing a ModCommand must happen inside a command (but outside a write action)
        CommandProcessor.getInstance().executeCommand(
            page.project,
            {
                ModCommandExecutor.getInstance().executeInteractively(context, LayoutPathChooser(page).choose(), editor)
            },
            e.presentation.text,
            null,
        )
    }
}

/**
 * An action in the menu of a default layout hint, which opts its page out of the layout by annotating it with
 * `@NoLayout`.
 */
class OptOutOfDefaultLayoutAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.defaultLayoutHintPage != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val page = e.defaultLayoutHintPage ?: return
        WriteCommandAction.writeCommandAction(page.project, page.containingFile)
            .withName(e.presentation.text)
            .run<Throwable> { page.addNoLayoutAnnotation() }
    }
}

/**
 * An action in the menu of our annotation hints, which opens the settings for them.
 *
 * Declarative inlay hints only get a "Disable" action in their menu by default, so we add this one ourselves.
 */
class ConfigureKobwebAnnotationHintsAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.getData(InlayHintsProvider.PROVIDER_ID) == KobwebAnnotationInlayHintsProvider.PROVIDER_ID
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        showInlaySettings(project, KotlinLanguage.INSTANCE) { model ->
            model.id == KobwebAnnotationInlayHintsProvider.PROVIDER_ID
        }
    }
}
