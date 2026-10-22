package com.varabyte.kobweb.intellij.quickfixes.layout

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.layout.LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.NO_LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.psi.deleteWithSurroundingWhitespace
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.psi.removeImportIfUnused
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Resolve a page being tagged with both `@Layout` and `@NoLayout` by removing one of them.
 *
 * The conflict is reported on both annotations, so this removes the annotation of type [fqn] from the page, whichever
 * annotation the fix was invoked on.
 */
sealed class RemoveConflictingLayoutAnnotationQuickFix(private val fqn: FqName) : PsiUpdateModCommandQuickFix() {
    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val page = element.getStrictParentOfType<KtNamedFunction>() ?: return
        val annotationEntry = page.annotationEntries.firstOrNull { it.isAnnotationOfType(fqn) } ?: return
        val file = page.containingKtFile

        annotationEntry.deleteWithSurroundingWhitespace()

        file.removeImportIfUnused(fqn)
    }
}

class RemoveLayoutQuickFix : RemoveConflictingLayoutAnnotationQuickFix(LAYOUT_FQN) {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.remove_layout.name")
}

class RemoveNoLayoutQuickFix : RemoveConflictingLayoutAnnotationQuickFix(NO_LAYOUT_FQN) {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.remove_no_layout.name")
}
