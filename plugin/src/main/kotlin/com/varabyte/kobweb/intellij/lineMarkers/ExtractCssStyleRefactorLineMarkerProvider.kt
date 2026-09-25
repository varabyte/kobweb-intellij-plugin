package com.varabyte.kobweb.intellij.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType
import com.varabyte.kobweb.intellij.wizards.cssstyle.ExtractCssStyleWizard
import com.varabyte.kobweb.intellij.wizards.cssstyle.ModifierChainInfo
import com.varabyte.kobweb.intellij.util.compose.STYLE_PROPERTY_VALUE_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.modifier.MODIFIER_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierCompanion
import com.varabyte.kobweb.intellij.util.kobweb.project.KobwebLineMarkerInfo
import com.varabyte.kobweb.intellij.util.kobweb.style.createStyleNameErrorValidator
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonCallableId
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonClassId
import com.varabyte.kobweb.intellij.util.text.truncate
import com.varabyte.kobweb.intellij.wizards.cssstyle.performRefactoring
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.isTopLevel
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

// TODO: Create a settings page as a way to reconfigure options chosen here

class ExtractCssStyleRefactorLineMarkerProvider : LineMarkerProvider {
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