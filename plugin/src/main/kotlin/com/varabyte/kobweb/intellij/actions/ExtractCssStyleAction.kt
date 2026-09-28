package com.varabyte.kobweb.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.varabyte.kobweb.intellij.util.features.ExtractCssStyleUtils
import com.varabyte.kobweb.intellij.util.idea.key
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile

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

        ExtractCssStyleUtils.handleExtractCssStyle(editor, modifierChainStart)
    }

    private fun findTargetModifierChain(e: AnActionEvent): KtDotQualifiedExpression? {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return null

        return ExtractCssStyleUtils.toInlineModifierChain(element, allowAnyElementInChain = true)
    }
}