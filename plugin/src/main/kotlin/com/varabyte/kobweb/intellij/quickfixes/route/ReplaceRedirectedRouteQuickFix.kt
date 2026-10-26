package com.varabyte.kobweb.intellij.quickfixes.route

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Replace the path in a route string with the one a redirect sends it to, keeping any query parameters or fragment,
 * e.g. `"/old-page?tab=2"` becomes `"/new-page?tab=2"`.
 */
class ReplaceRedirectedRouteQuickFix(private val newPath: String) : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.replace_redirected_route.family.name")
    override fun getName() = UxGlobals.textBundle.message("quickfixes.replace_route_value.name", newPath)

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val stringExpr = element as? KtStringTemplateExpression ?: return
        ElementManipulators.handleContentChange(stringExpr, stringExpr.linkPathRange(), newPath)
    }
}
