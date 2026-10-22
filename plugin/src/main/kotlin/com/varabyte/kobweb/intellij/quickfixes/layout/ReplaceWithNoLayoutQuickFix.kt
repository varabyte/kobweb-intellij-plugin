package com.varabyte.kobweb.intellij.quickfixes.layout

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.layout.LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.NO_LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.psi.addImportIfNeeded
import com.varabyte.kobweb.intellij.util.psi.removeImportIfUnused
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Replace a page's `@Layout` annotation with `@NoLayout`, for when the user meant to opt the page out of a layout.
 */
class ReplaceWithNoLayoutQuickFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.replace_with_no_layout.name")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val annotationEntry = element as? KtAnnotationEntry ?: return
        val file = annotationEntry.containingKtFile
        val psiFactory = KtPsiFactory(project)

        annotationEntry.replace(psiFactory.createAnnotationEntry("@${NO_LAYOUT_FQN.shortName()}"))
        file.addImportIfNeeded(NO_LAYOUT_FQN)
        file.removeImportIfUnused(LAYOUT_FQN)
    }
}
