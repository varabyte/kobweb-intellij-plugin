package com.varabyte.kobweb.intellij.quickfixes.route

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/**
 * Remove a `@Page` annotation's route override when Kobweb would generate the same route without it.
 */
class RemoveRouteOverrideQuickFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.remove_route_override.name")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val pageAnnotation = element.parentOfType<KtAnnotationEntry>(withSelf = true) ?: return
        pageAnnotation.valueArgumentList?.delete()
    }
}
