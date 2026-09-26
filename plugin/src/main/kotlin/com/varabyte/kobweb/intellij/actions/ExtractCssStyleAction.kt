package com.varabyte.kobweb.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.actions.ExtractCssStyleUtils.handleExtractCssStyle
import com.varabyte.kobweb.intellij.util.idea.key
import com.varabyte.kobweb.intellij.util.kobweb.modifier.MODIFIER_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierCompanion
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonCallableId
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonClassId
import com.varabyte.kobweb.intellij.wizards.cssstyle.ExtractCssStyleWizard
import com.varabyte.kobweb.intellij.wizards.cssstyle.performRefactoring
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.*

object ExtractCssStyleUtils {
    // An inline modifier chain exists in the user's normal code; ignore modifier chains declared inside
    // special CssStyle, CssStyleVariant, and Keyframes classes.
    private fun isModifierChainInsideExcludedContext(element: PsiElement): Boolean {
        var curr: PsiElement? = element.parent
        while (curr != null && curr !is KtFile) {
            if (curr is KtCallExpression) {
                analyze(curr) {
                    if (curr.styleSingletonCallableId != null) return true
                }
            } else if (curr is KtClass) {
                analyze(curr) {
                    if (curr.expressionType?.styleSingletonClassId != null) return true
                }
            }
            curr = curr.parent
        }
        return false
    }

    fun toInlineModifierChain(element: PsiElement): KtDotQualifiedExpression? {
        val element = if (element is LeafPsiElement) element.parent else element
        val namedExpression = element as? KtNameReferenceExpression ?: return null
        // Early quick check abort to avoid potentially unnecessary analyze
        if (namedExpression.text != MODIFIER_CLASS_ID.shortClassName.identifier) return null

        // Get the element as the first item of a modifier chain
        val modifierChainStart = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java) ?: return null
        // Should never happen but think of this like an assertion that we ARE the first in the chain
        if (modifierChainStart.receiverExpression != element) return null

        analyze(namedExpression) {
            if (!namedExpression.isModifierCompanion()) return null
        }

        if (isModifierChainInsideExcludedContext(modifierChainStart)) return null

        return modifierChainStart
    }

    fun handleExtractCssStyle(editor: Editor, modifierChainStart: KtDotQualifiedExpression) {
        val project = modifierChainStart.project
        val wizard = ExtractCssStyleWizard(project, ExtractCssStyleWizard.Input(modifierChainStart))
        val wizardResult = wizard.show() ?: return
        wizardResult.performRefactoring(editor, modifierChainStart)
    }
}

private val TARGET_MODIFIER_CHAIN_KEY by key<KtDotQualifiedExpression>()


class ExtractCssStyleAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val targetModifierChain = findTargetModifierChain(e)
        // Enable and make visible only if a valid Modifier chain is selected/targeted
        e.presentation.isEnabledAndVisible = targetModifierChain != null

        targetModifierChain?.let { e.presentation.putClientProperty(TARGET_MODIFIER_CHAIN_KEY, it) }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val modifierChainStart = e.presentation.getClientProperty(TARGET_MODIFIER_CHAIN_KEY) ?: return

        handleExtractCssStyle(editor, modifierChainStart)
    }

    private fun findTargetModifierChain(e: AnActionEvent): KtDotQualifiedExpression? {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return null

        return ExtractCssStyleUtils.toInlineModifierChain(element)
    }
}