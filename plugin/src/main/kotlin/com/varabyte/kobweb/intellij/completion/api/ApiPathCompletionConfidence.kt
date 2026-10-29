package com.varabyte.kobweb.intellij.completion.api

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import com.varabyte.kobweb.intellij.completion.route.findPathPrefix
import com.varabyte.kobweb.intellij.util.kobweb.api.mayHaveApiPathTarget
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Allow completion popups to appear automatically while typing an API path, e.g. `window.api.get("he")`.
 *
 * By default, the IDE suppresses auto-popups inside string literals (where, normally, completion suggestions would
 * just be noise). API paths are an exception, as we can suggest the paths of API endpoints.
 *
 * Note that this only checks the name of what the string is used with (see [mayHaveApiPathTarget]), as checking
 * whether it actually holds an API path requires Kotlin analysis, which is left to [ApiPathCompletionContributor].
 */
class ApiPathCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        val stringExpr = contextElement.parentOfType<KtStringTemplateExpression>(withSelf = true)
            ?: return ThreeState.UNSURE
        if (stringExpr.findPathPrefix(offset) == null) return ThreeState.UNSURE
        return if (stringExpr.mayHaveApiPathTarget()) ThreeState.NO else ThreeState.UNSURE
    }
}
