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
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierCompanion
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonCallableId
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonClassId
import com.varabyte.kobweb.intellij.util.psi.getEntireDotQualifiedExpression
import com.varabyte.kobweb.intellij.util.psi.getRootReceiverExpression
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

    /**
     * Convert [element] to a [KtDotQualifiedExpression] representing a `Modifier` chain.
     *
     * @param allowAnyElementInChain If true, any element inside the modifier chain can be specified. Otherwise, _only_
     *   the initial `Modifier` element is allowed.
     */
    fun toInlineModifierChain(element: PsiElement, allowAnyElementInChain: Boolean = false): KtDotQualifiedExpression? {
        val element = if (element is LeafPsiElement) element.parent else element

        // Get the element as the first item of a modifier chain
        val modifierChainStart = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
            ?.getEntireDotQualifiedExpression()
            ?: return null

        val namedExpression = modifierChainStart.getRootReceiverExpression() as? KtNameReferenceExpression ?: return null
        if (!allowAnyElementInChain && element != namedExpression) return null

        // We've done quick early abort checks so far -- let's do a type safe check to really make sure
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

        return ExtractCssStyleUtils.toInlineModifierChain(element, allowAnyElementInChain = true)
    }
}