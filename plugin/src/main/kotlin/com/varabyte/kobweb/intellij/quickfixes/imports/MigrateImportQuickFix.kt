package com.varabyte.kobweb.intellij.quickfixes.imports

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.varabyte.kobweb.intellij.util.psi.replaceImportedFqName
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * Change an import to the place its declaration moved to, e.g. `import com.varabyte.kobweb.browser.api` becomes
 * `import com.varabyte.kobweb.api.api`.
 */
class MigrateImportQuickFix(private val newFqName: String) : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.migrate_import.family.name")
    override fun getName() = UxGlobals.textBundle.message("quickfixes.migrate_import.name", newFqName)

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val importDirective = element.parentOfType<KtImportDirective>(withSelf = true) ?: return
        importDirective.replaceImportedFqName(FqName(newFqName))
    }
}
