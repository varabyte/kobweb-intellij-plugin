package com.varabyte.kobweb.intellij.completion.route

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Allow completion popups to appear automatically while typing a route, e.g. `Link("/bl")`.
 *
 * By default, the IDE suppresses auto-popups inside string literals (where, normally, completion suggestions would
 * just be noise). Routes are an exception, as we can suggest the routes of pages.
 *
 * Note that this only checks that the string looks like a route, as checking whether it's used with something that
 * holds one requires Kotlin analysis, which is left to [RouteCompletionContributor]. (If it isn't, no popup appears,
 * since there's nothing to suggest.)
 */
class RouteCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        val stringExpr = contextElement.parentOfType<KtStringTemplateExpression>(withSelf = true)
            ?: return ThreeState.UNSURE
        val prefix = stringExpr.findRoutePrefix(offset) ?: return ThreeState.UNSURE
        return if (prefix.startsWith('/')) ThreeState.NO else ThreeState.UNSURE
    }
}
