package com.varabyte.kobweb.intellij.quickfixes.layout

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.layout.NO_LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.psi.isInScopeAs
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Replace a page's `@Layout` annotation with `@NoLayout`, for when the user meant to opt the page out of a layout.
 */
class ReplaceWithNoLayoutQuickFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.replace_with_no_layout.name")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val annotationEntry = element as? KtAnnotationEntry ?: return
        val file = annotationEntry.containingKtFile
        val psiFactory = KtPsiFactory(project)

        val shortName = NO_LAYOUT_FQN.shortName().asString()
        annotationEntry.replace(psiFactory.createAnnotationEntry("@$shortName"))

        if (!file.isInScopeAs(NO_LAYOUT_FQN, shortName)) {
            val importList = file.importList ?: return
            val newImport = psiFactory.createImportDirective(ImportPath(NO_LAYOUT_FQN, false))
            // Keep imports sorted, which is how the IDE (and most users) order them
            val nextImport = importList.imports.firstOrNull { import ->
                (import.importedFqName?.asString() ?: "") > NO_LAYOUT_FQN.asString()
            }
            if (nextImport != null) importList.addBefore(newImport, nextImport) else importList.add(newImport)
        }
    }
}
