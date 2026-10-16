package com.varabyte.kobweb.intellij.inlay

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.varabyte.kobweb.intellij.util.kobweb.isInKobwebSource
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierChainingExtension
import com.varabyte.kobweb.intellij.util.psi.resolveToKtNamedFunction
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtCallExpression

class KobwebModifierInlayHintsProvider : InlayHintsProvider {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        if (!file.isInReadableKobwebProject() && !file.isInKobwebSource()) return null

        return object : SharedBypassCollector {
            override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
                val callExpr = element as? KtCallExpression ?: return
                val function = analyze(callExpr) {
                    callExpr.resolveToKtNamedFunction()?.takeIf { it.isModifierChainingExtension() }
                } ?: return

                val (optionId, badge) = when (function.getWebModifierType()) {
                    WebModifierType.ATTRS -> ATTR_OPTION to "attr"
                    WebModifierType.STYLE -> STYLE_OPTION to "style"
                    else -> return
                }
                sink.whenOptionEnabled(optionId) {
                    sink.addPresentation(
                        InlineInlayPosition(element.textRange.endOffset, relatedToPrevious = true),
                        hintFormat = HintFormat.default,
                    ) { text(badge) }
                }
            }
        }
    }

    companion object {
        const val ATTR_OPTION = "kobweb.modifier.hints.attr"
        const val STYLE_OPTION = "kobweb.modifier.hints.style"
    }
}