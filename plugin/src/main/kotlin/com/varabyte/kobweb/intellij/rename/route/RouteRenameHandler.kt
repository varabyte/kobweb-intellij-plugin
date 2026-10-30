package com.varabyte.kobweb.intellij.rename.route

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.varabyte.kobweb.intellij.references.api.ApiPathReference
import com.varabyte.kobweb.intellij.references.route.RouteReference
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Refuses to rename from a route that leads to a page or an API endpoint, e.g. `Link("/about")` or `ApiStream("chat")`,
 * and explains why instead.
 *
 * Otherwise, IntelliJ would rename what the route leads to (e.g. the `AboutPage` function), which leaves the route
 * itself unchanged, since Kobweb derives it from where that declaration lives, not its name.
 *
 * Routes that lead to public files (e.g. `"/images/cat.png"`) can still be renamed from, as that renames the file, which
 * does change the route.
 *
 * NOTE: This is registered with the Kotlin plugin's rename handlers, not IntelliJ's. When more than one of IntelliJ's
 * rename handlers is available, it asks the user which one to use (regardless of their order), and the Kotlin plugin's
 * handler is always available in Kotlin files. That handler, on the other hand, quietly picks the first of its own
 * handlers that's available, so registering this one first among them lets it win.
 */
class RouteRenameHandler : RenameHandler {
    /**
     * Returns the page or API endpoint that the route at the editor's caret leads to, if any.
     *
     * This uses the element that IntelliJ already found to rename ([CommonDataKeys.PSI_ELEMENT]), rather than resolving
     * the route's reference ourselves, as this may be called on the UI thread, where resolving an API path's reference
     * (which needs Kotlin analysis) isn't allowed.
     */
    private fun DataContext.findRouteTarget(): KtCallableDeclaration? {
        val editor = getData(CommonDataKeys.EDITOR) ?: return null
        val file = getData(CommonDataKeys.PSI_FILE) ?: return null
        val target = getData(CommonDataKeys.PSI_ELEMENT) as? KtCallableDeclaration ?: return null
        val stringExpr = file.findElementAt(editor.caretModel.offset)
            ?.parentOfType<KtStringTemplateExpression>(withSelf = true)
            ?: return null
        return target.takeIf { stringExpr.references.any { it is RouteReference || it is ApiPathReference } }
    }

    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean = dataContext.findRouteTarget() != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext) {
        val target = dataContext.findRouteTarget() ?: return
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            UxGlobals.textBundle.message(
                "rename.route.cannot.rename.route",
                target.name.orEmpty(),
                target.containingKtFile.name
            ),
            RefactoringBundle.message("rename.title"),
            null,
        )
    }

    // Only called when renaming outside an editor (e.g. from the Project view), where there's no route to rename from
    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) = Unit
}
