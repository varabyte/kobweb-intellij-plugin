package com.varabyte.kobweb.intellij.completion.route

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Allow completion popups to appear automatically while typing a key into route params, e.g. `ctx.route.params["p"]`
 * or `ctx.req.params["p"]`.
 *
 * By default, the IDE suppresses auto-popups inside string literals (where, normally, completion suggestions would
 * just be noise). Route param keys are an exception, as we can suggest the names of the route's dynamic segments.
 *
 * Note that this only checks the name of the map the string is used with (see [findRouteParamPrefix]), as checking
 * whether it really holds route params requires Kotlin analysis, which is left to [RouteParamCompletionContributor].
 */
class RouteParamCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        val stringExpr = contextElement.parentOfType<KtStringTemplateExpression>(withSelf = true)
            ?: return ThreeState.UNSURE
        return if (stringExpr.findRouteParamPrefix(offset) != null) ThreeState.NO else ThreeState.UNSURE
    }
}
