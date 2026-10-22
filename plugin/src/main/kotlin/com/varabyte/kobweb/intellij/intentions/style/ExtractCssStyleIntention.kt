package com.varabyte.kobweb.intellij.intentions.style

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.features.extractCssStyle
import com.varabyte.kobweb.intellij.util.features.toInlineModifierChain
import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

private val DERIVED_MODIFIER_CHAIN_KEY by CacheDerivedPsiElementIntentionAction.key<KtDotQualifiedExpression>()

class ExtractCssStyleIntention : CacheDerivedPsiElementIntentionAction<KtDotQualifiedExpression>(DERIVED_MODIFIER_CHAIN_KEY,
    UxGlobals.textBundle.message("intentions.extract_css_style.name")
) {
    override fun startInWriteAction(): Boolean = false // We open a wizard which will handle write action behavior

    override fun PsiElement.tryDerivingElement(): KtDotQualifiedExpression? {
        if (!isInWritableKobwebProject()) return null
        return toInlineModifierChain(allowAnyElementInChain = true)
    }

    override fun handleElementIsInvoked(project: Project, editor: Editor, element: KtDotQualifiedExpression) {
        element.extractCssStyle(editor)
    }
}