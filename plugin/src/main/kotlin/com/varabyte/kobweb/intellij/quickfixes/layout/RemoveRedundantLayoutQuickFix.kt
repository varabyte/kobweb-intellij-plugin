package com.varabyte.kobweb.intellij.quickfixes.layout

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.layout.LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.psi.deleteWithSurroundingWhitespace
import com.varabyte.kobweb.intellij.util.psi.removeImportIfUnused
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/**
 * Remove a page's `@Layout` annotation when it points at the default layout the page would use anyway.
 */
class RemoveRedundantLayoutQuickFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.remove_redundant_layout.name")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val annotationEntry = element as? KtAnnotationEntry ?: return
        val file = annotationEntry.containingKtFile

        annotationEntry.deleteWithSurroundingWhitespace()

        file.removeImportIfUnused(LAYOUT_FQN)
    }
}
