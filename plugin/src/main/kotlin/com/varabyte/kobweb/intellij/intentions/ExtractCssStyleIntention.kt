package com.varabyte.kobweb.intellij.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.removeUserData
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.features.ExtractCssStyleUtils
import com.varabyte.kobweb.intellij.util.idea.key
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import com.varabyte.kobweb.intellij.wizards.cssstyle.ExtractCssStyleWizard
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import javax.swing.SwingUtilities

private val CACHED_BY_ELEMENT_MODIFIER_CHAIN_KEY by key<Pair<PsiElement, KtDotQualifiedExpression>>()

class ExtractCssStyleIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName(): String = UxGlobals.FAMILY_NAME
    override fun getText(): String = ExtractCssStyleWizard.TITLE
    override fun startInWriteAction(): Boolean = false

    private fun PsiElement.getCachedInlineModifierChain(editor: Editor): KtDotQualifiedExpression? {
        val self = this
        return editor.getUserData(CACHED_BY_ELEMENT_MODIFIER_CHAIN_KEY)?.let { (element, modifierChain) ->
            modifierChain.takeIf { element == self }
        }
    }

    private fun PsiElement.toInlineModifierChain(editor: Editor): KtDotQualifiedExpression? {
        if (SwingUtilities.isEventDispatchThread()) {
            return this.getCachedInlineModifierChain(editor)
        }

        return this.getCachedInlineModifierChain(editor) ?:
            ExtractCssStyleUtils.toInlineModifierChain(
                this,
                allowAnyElementInChain = true
            ).also { modifierChain ->
                if (modifierChain != null) {
                    editor.putUserData(CACHED_BY_ELEMENT_MODIFIER_CHAIN_KEY, this to modifierChain)
                } else {
                    editor.removeUserData(CACHED_BY_ELEMENT_MODIFIER_CHAIN_KEY)
                }
            }
    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        return element.toInlineModifierChain(editor) != null
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        element.toInlineModifierChain(editor)
            ?.let { modifierChain -> ExtractCssStyleUtils.handleExtractCssStyle(editor, modifierChain) }
            ?.also { editor.removeUserData(CACHED_BY_ELEMENT_MODIFIER_CHAIN_KEY) }
    }

}