package com.varabyte.kobweb.intellij.lineMarkers.style

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.varabyte.kobweb.intellij.util.features.ExtractCssStyleUtils
import com.varabyte.kobweb.intellij.util.kobweb.project.KobwebLineMarkerInfo
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import com.varabyte.kobweb.intellij.wizards.style.ExtractCssStyleWizard

class ExtractCssStyleRefactorLineMarkerProvider : LineMarkerProviderDescriptor() {

    @Suppress("DialogTitleCapitalization")
    override fun getName() = "Extract CssStyle"
    override fun getIcon() = UxGlobals.gutterIcon

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is LeafPsiElement) return null // The docs for this class say it should ideally point at leaf elements
        val modifierChainStart = ExtractCssStyleUtils.toInlineModifierChain(element) ?: return null

        return KobwebLineMarkerInfo(
            element,
            ExtractCssStyleWizard.TITLE,
            navHandler = { e, _ ->
                val dataContext = ActionToolbar.getDataContextFor(e.component)
                val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return@KobwebLineMarkerInfo

                ExtractCssStyleUtils.handleExtractCssStyle(editor, modifierChainStart)
            },
        )
    }
}