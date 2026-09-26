package com.varabyte.kobweb.intellij.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.util.kobweb.modifier.MODIFIER_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierCompanion
import com.varabyte.kobweb.intellij.util.kobweb.project.KobwebLineMarkerInfo
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonCallableId
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonClassId
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import com.varabyte.kobweb.intellij.wizards.cssstyle.ExtractCssStyleWizard
import com.varabyte.kobweb.intellij.wizards.cssstyle.performRefactoring
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.*

// TODO: Create a settings page as a way to reconfigure options chosen here

class ExtractCssStyleRefactorLineMarkerProvider : LineMarkerProviderDescriptor() {

    @Suppress("DialogTitleCapitalization")
    override fun getName() = "Extract CssStyle"
    override fun getIcon() = UxGlobals.gutterIcon

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is LeafPsiElement) return null // The docs for this class say it should ideally point at leaf elements
        val parent = element.parent ?: return null
        val namedExpression = parent as? KtNameReferenceExpression ?: return null
        // Early quick check abort to avoid potentially unnecessary analyze
        if (namedExpression.text != MODIFIER_CLASS_ID.shortClassName.identifier) return null

        // Get the element as the first item of a modifier chain
        val modifierChainStart = PsiTreeUtil.getParentOfType(parent, KtDotQualifiedExpression::class.java) ?: return null
        // Should never happen but think of this like an assertion that we ARE the first in the chain
        if (modifierChainStart.receiverExpression != parent) return null

        analyze(namedExpression) {
            if (!namedExpression.isModifierCompanion()) return null
        }

        if (isInsideExcludedContext(modifierChainStart)) return null

        return KobwebLineMarkerInfo(
            element,
            ExtractCssStyleWizard.TITLE,
            navHandler = { e, _ ->
                val dataContext = ActionToolbar.getDataContextFor(e.component)
                val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return@KobwebLineMarkerInfo

                handleExtractCssStyle(editor, modifierChainStart)
            },
        )
    }

    private fun isInsideExcludedContext(element: PsiElement): Boolean {
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

    private fun handleExtractCssStyle(editor: Editor, modifierChainStart: KtDotQualifiedExpression) {
        val project = modifierChainStart.project
        val wizard = ExtractCssStyleWizard(project, ExtractCssStyleWizard.Input(modifierChainStart))
        val wizardResult = wizard.show() ?: return
        wizardResult.performRefactoring(editor, modifierChainStart)
    }
}