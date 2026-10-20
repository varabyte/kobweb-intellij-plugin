package com.varabyte.kobweb.intellij.completion.layout

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.varabyte.kobweb.intellij.references.layout.LayoutPathReference
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

private fun PsiElement.isInsideLayoutPath(): Boolean {
    val stringExpr = PsiTreeUtil.getParentOfType(this, KtStringTemplateExpression::class.java, false) ?: return false
    return stringExpr.references.any { it is LayoutPathReference }
}

/**
 * Allow completion popups to appear automatically while typing inside `@Layout("...")` paths.
 *
 * By default, the IDE suppresses auto-popups inside string literals (where, normally, completion suggestions would
 * just be noise). Layout paths are an exception, as their contents are effectively code.
 */
class LayoutPathCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        return if (contextElement.isInsideLayoutPath()) ThreeState.NO else ThreeState.UNSURE
    }
}
