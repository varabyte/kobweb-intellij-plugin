package com.varabyte.kobweb.intellij.inspections.layout

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.layout.RemoveDefaultLayoutQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.findDuplicateDefaultLayouts
import com.varabyte.kobweb.intellij.util.kobweb.layout.hasLayoutPath
import com.varabyte.kobweb.intellij.util.kobweb.layout.kobwebDisplayPath
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.qualifiedPagesPackage
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports default layouts (i.e. `@file:Layout`) that Kobweb rejects at build time.
 *
 * (Default layouts without a path are reported by [InvalidLayoutPathInspection].)
 */
class InvalidDefaultLayoutInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
                super.visitAnnotationEntry(annotationEntry)
                if (annotationEntry.useSiteTarget?.getAnnotationUseSiteTarget() != AnnotationUseSiteTarget.FILE) return
                if (!annotationEntry.isAnnotationOfType(LAYOUT_FQN)) return

                val pagesPackage = annotationEntry.findKobwebProject()?.qualifiedPagesPackage ?: return
                val pkg = annotationEntry.containingKtFile.packageFqName
                if (!pkg.isSubpackageOf(pagesPackage)) {
                    holder.registerProblem(
                        annotationEntry,
                        UxGlobals.textBundle.message(
                            "inspection.invalid.default.layout.outside.pages.package.display.text",
                            pagesPackage.asString(),
                        ),
                    )
                    return
                }

                // A default layout without a path is already an error, and Kobweb doesn't count it as a duplicate
                if (!annotationEntry.hasLayoutPath) return
                val duplicates = annotationEntry.findDuplicateDefaultLayouts()
                if (duplicates.isNotEmpty()) {
                    holder.registerProblem(
                        annotationEntry,
                        UxGlobals.textBundle.message(
                            "inspection.invalid.default.layout.duplicate.display.text",
                            pkg.asString(),
                            duplicates
                                .map { it.containingKtFile.kobwebDisplayPath }
                                .sorted()
                                .joinToString { "\"$it\"" },
                        ),
                        RemoveDefaultLayoutQuickFix(),
                    )
                }
            }
        }
    }
}
