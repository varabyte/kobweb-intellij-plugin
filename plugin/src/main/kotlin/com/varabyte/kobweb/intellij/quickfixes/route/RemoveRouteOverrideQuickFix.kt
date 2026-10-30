package com.varabyte.kobweb.intellij.quickfixes.route

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/**
 * Remove a route override (e.g. in `@Page` or `@Api`) when Kobweb would generate the same route without it.
 */
class RemoveRouteOverrideQuickFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.remove_route_override.name")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val annotation = element.parentOfType<KtAnnotationEntry>(withSelf = true) ?: return
        annotation.valueArgumentList?.delete()
    }
}
