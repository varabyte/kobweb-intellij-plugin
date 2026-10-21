package com.varabyte.kobweb.intellij.quickfixes

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class ReplaceCalleeQuickFix(private val newFqName: FqName) : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.replace_callee.name", newFqName.shortName())

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val call = element as? KtCallExpression ?: element.parent as? KtCallExpression ?: return
        val callee = call.calleeExpression as? KtNameReferenceExpression ?: return

        callee.mainReference.bindToFqName(newFqName)
    }
}