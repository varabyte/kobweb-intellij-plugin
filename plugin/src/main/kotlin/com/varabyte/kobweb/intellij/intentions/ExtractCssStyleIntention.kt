package com.varabyte.kobweb.intellij.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.features.ExtractCssStyleUtils
import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.wizards.cssstyle.ExtractCssStyleWizard
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

private val DERIVED_MODIFIER_CHAIN_KEY by CacheDerivedPsiElementIntentionAction.key<KtDotQualifiedExpression>()

class ExtractCssStyleIntention : CacheDerivedPsiElementIntentionAction<KtDotQualifiedExpression>(DERIVED_MODIFIER_CHAIN_KEY) {
    override fun getText(): String = ExtractCssStyleWizard.TITLE

    override fun PsiElement.tryDerivingElement(): KtDotQualifiedExpression? {
        return ExtractCssStyleUtils.toInlineModifierChain(this, allowAnyElementInChain = true)
    }

    override fun handleElementIsInvoked(editor: Editor, element: KtDotQualifiedExpression) {
        ExtractCssStyleUtils.handleExtractCssStyle(editor, element)
    }
}