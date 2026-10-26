package com.varabyte.kobweb.intellij.quickfixes.route

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Replace a value that Kobweb builds routes from (i.e. a `@Page` route override or a `@file:PackageMapping`) with a
 * corrected or simpler one.
 *
 * @param priority Where this fix is listed among others, e.g. LOW for a less likely fix offered alongside a better guess.
 */
class ReplaceRouteValueQuickFix(
    private val newValue: String,
    private val priority: PriorityAction.Priority = PriorityAction.Priority.NORMAL,
) : PsiUpdateModCommandQuickFix(), PriorityAction {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.replace_route_value.family.name")
    override fun getName() = UxGlobals.textBundle.message("quickfixes.replace_route_value.name", newValue)
    override fun getPriority() = priority

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val routeValueExpr = element as? KtStringTemplateExpression ?: return
        ElementManipulators.handleContentChange(routeValueExpr, newValue)
    }
}
