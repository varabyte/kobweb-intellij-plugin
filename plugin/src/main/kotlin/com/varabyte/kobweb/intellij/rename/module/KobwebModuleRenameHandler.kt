package com.varabyte.kobweb.intellij.rename.module

import com.intellij.ide.TitledHandler
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenamePsiElementProcessor

/**
 * Makes "Rename module" on a Kobweb module's folder rename the module with [KobwebModuleRenameProcessor].
 *
 * IntelliJ's own "Rename module" only renames the IDE's module, which Gradle sync then names after the Gradle project
 * again, and leaves the folder and build scripts alone. This handler takes its place: IntelliJ lists one handler per
 * title, the last one registered, so this one has the same title and is registered last.
 */
class KobwebModuleRenameHandler : RenameHandler, TitledHandler {
    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        if (LangDataKeys.MODULE_CONTEXT.getData(dataContext) == null) return false
        return dataContext.findModuleDir() != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext) {
        invoke(project, PsiElement.EMPTY_ARRAY, dataContext)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
        val dir = dataContext.findModuleDir() ?: return
        PsiElementRenameHandler.rename(
            dir,
            project,
            dir,
            CommonDataKeys.EDITOR.getData(dataContext),
            PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext),
        )
    }

    override fun getActionTitle(): String = RefactoringBundle.message("rename.module.title")

    // KobwebModuleRenameProcessor decides which folders qualify, so this handler is only offered for local Kobweb
    // modules. Other modules keep IntelliJ's own "Rename module".
    private fun DataContext.findModuleDir(): PsiDirectory? {
        val dir = PsiElementRenameHandler.getElement(this) as? PsiDirectory ?: return null
        return dir.takeIf { RenamePsiElementProcessor.forElement(it) is KobwebModuleRenameProcessor }
    }
}
