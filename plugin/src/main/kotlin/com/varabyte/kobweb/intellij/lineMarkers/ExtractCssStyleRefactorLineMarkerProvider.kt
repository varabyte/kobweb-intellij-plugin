package com.varabyte.kobweb.intellij.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.util.features.ExtractCssStyleUtils
import com.varabyte.kobweb.intellij.util.kobweb.project.KobwebLineMarkerInfo
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import com.varabyte.kobweb.intellij.wizards.cssstyle.ExtractCssStyleWizard
import org.jetbrains.kotlin.psi.KtProperty

class ExtractCssStyleRefactorLineMarkerProvider : LineMarkerProviderDescriptor() {

    @Suppress("DialogTitleCapitalization")
    override fun getName() = "Extract CssStyle"
    override fun getIcon() = UxGlobals.gutterIcon

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is LeafPsiElement) return null // The docs for this class say it should ideally point at leaf elements
        val modifierChainStart = ExtractCssStyleUtils.toInlineModifierChain(element) ?: return null

        // It can be common for people to define modifier parts in variables, like `val SIZE_MODIFIER = Modifier...`
        // In order to cut down on excessive line markers showing up in those cases, let's just avoid creating line
        // markers for them. (You can still extract CssStyles for those cases using ALT+ENTER / Refactor tools.)
        if (PsiTreeUtil.getParentOfType(element, KtProperty::class.java) != null) {
            return null
        }

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