package com.varabyte.kobweb.intellij.inspections.layout

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.layout.RemoveRedundantLayoutQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.PackageShortcuts
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.NO_LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.displayPath
import com.varabyte.kobweb.intellij.util.kobweb.layout.findPackageDefaultLayout
import com.varabyte.kobweb.intellij.util.kobweb.layout.kobwebGroup
import com.varabyte.kobweb.intellij.util.kobweb.layout.layoutPathExpression
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports `@Layout` annotations on pages that point at the default layout the page would use anyway.
 *
 * This is only a weak warning, as some users may prefer to be explicit (e.g. so the page keeps its layout even if the
 * default changes later).
 */
class RedundantLayoutInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
                super.visitAnnotationEntry(annotationEntry)
                if (!annotationEntry.isAnnotationOfType(LAYOUT_FQN)) return

                val page = (annotationEntry.parent as? KtModifierList)?.parent as? KtNamedFunction ?: return
                // Pages tagged with both `@Layout` and `@NoLayout` are already an error (which Kobweb reports), so
                // don't pile on
                if (page.annotationEntries.any { it.isAnnotationOfType(NO_LAYOUT_FQN) }) return
                val defaultLayout = page.findPackageDefaultLayout() ?: return

                val pathExpr = annotationEntry.layoutPathExpression ?: return
                val layoutFqn = PackageShortcuts.resolve(page.kobwebGroup, ElementManipulators.getValueText(pathExpr))
                    ?: return
                if (layoutFqn.asString() != defaultLayout.layout.fqn) return

                holder.registerProblem(
                    annotationEntry,
                    UxGlobals.textBundle.message("inspection.redundant.layout.display.text", defaultLayout.displayPath),
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                    RemoveRedundantLayoutQuickFix(),
                )
            }
        }
    }
}
