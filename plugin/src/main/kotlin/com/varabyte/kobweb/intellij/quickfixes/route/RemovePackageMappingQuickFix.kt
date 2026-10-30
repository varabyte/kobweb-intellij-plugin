package com.varabyte.kobweb.intellij.quickfixes.route

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteKind
import com.varabyte.kobweb.intellij.util.kobweb.route.packageMappingFqn
import com.varabyte.kobweb.intellij.util.psi.deleteWithSurroundingWhitespace
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.psi.removeImportIfUnused
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/**
 * Remove a `@file:PackageMapping` annotation, e.g. one that maps a package to the name Kobweb would use for it anyway.
 *
 * This can be applied to the annotation or anything inside it. See also [DeletePackageMappingFileQuickFix], for when
 * the annotation is all its file contains.
 */
class RemovePackageMappingQuickFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.remove_package_mapping.name")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val annotationEntry = element.parentOfType<KtAnnotationEntry>(withSelf = true) ?: return
        val file = annotationEntry.containingKtFile

        val packageMappingFqn = RouteKind.entries
            .map { kind -> kind.packageMappingFqn }
            .firstOrNull { fqn -> annotationEntry.isAnnotationOfType(fqn) }

        annotationEntry.deleteWithSurroundingWhitespace()

        packageMappingFqn?.let(file::removeImportIfUnused)
    }
}
