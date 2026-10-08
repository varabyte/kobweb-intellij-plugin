package com.varabyte.kobweb.intellij.intentions.style

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.features.ExtractCssStyleUtils
import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.wizards.style.ExtractCssStyleWizard
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

private val DERIVED_MODIFIER_CHAIN_KEY by CacheDerivedPsiElementIntentionAction.key<KtDotQualifiedExpression>()

class ExtractCssStyleIntention : CacheDerivedPsiElementIntentionAction<KtDotQualifiedExpression>(DERIVED_MODIFIER_CHAIN_KEY) {
    override fun getText(): String = ExtractCssStyleWizard.TITLE
    override fun startInWriteAction(): Boolean = false // We open a wizard which will handle write action behavior

    override fun PsiElement.tryDerivingElement(): KtDotQualifiedExpression? {
        return ExtractCssStyleUtils.toInlineModifierChain(this, allowAnyElementInChain = true)
    }

    override fun handleElementIsInvoked(project: Project, editor: Editor, element: KtDotQualifiedExpression) {
        ExtractCssStyleUtils.handleExtractCssStyle(editor, element)
    }
}