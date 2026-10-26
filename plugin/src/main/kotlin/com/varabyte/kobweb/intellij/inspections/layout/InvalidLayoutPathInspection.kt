package com.varabyte.kobweb.intellij.inspections.layout

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.layout.ChooseLayoutQuickFix
import com.varabyte.kobweb.intellij.quickfixes.layout.RemoveLayoutQuickFix
import com.varabyte.kobweb.intellij.quickfixes.layout.ReplaceWithNoLayoutQuickFix
import com.varabyte.kobweb.intellij.references.layout.LayoutPathReference
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.NO_LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.PAGE_FQN
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports `@Layout` annotations that Kobweb will reject at build time, i.e. ones whose path doesn't point at a layout,
 * or ones on a page or file (i.e. a default layout) that don't specify a path at all.
 */
class InvalidLayoutPathInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
                super.visitAnnotationEntry(annotationEntry)
                if (!annotationEntry.isAnnotationOfType(LAYOUT_FQN)) return

                // A layout without a path just means it has no parent layout, but default layouts and pages must
                // specify one
                fun reportMissingPath() {
                    if (annotationEntry.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.FILE) {
                        holder.registerProblem(
                            annotationEntry,
                            UxGlobals.textBundle.message("inspection.invalid.layout.path.missing.on.file.display.text"),
                            ChooseLayoutQuickFix(),
                        )
                        return
                    }

                    val function = (annotationEntry.parent as? KtModifierList)?.parent as? KtNamedFunction ?: return
                    if (function.annotationEntries.none { it.isAnnotationOfType(PAGE_FQN) }) return

                    // If the page already opts out with `@NoLayout`, replacing `@Layout` with it would duplicate it
                    val hasNoLayout = function.annotationEntries.any { it.isAnnotationOfType(NO_LAYOUT_FQN) }
                    holder.registerProblem(
                        annotationEntry,
                        UxGlobals.textBundle.message("inspection.invalid.layout.path.missing.on.page.display.text"),
                        if (hasNoLayout) RemoveLayoutQuickFix() else ReplaceWithNoLayoutQuickFix(),
                        ChooseLayoutQuickFix(),
                    )
                }

                val argument = annotationEntry.valueArguments.firstOrNull() ?: return reportMissingPath()
                // If the path isn't a string literal (e.g. it's a constant), we can't check it
                val pathExpr = argument.getArgumentExpression() as? KtStringTemplateExpression ?: return
                val reference = pathExpr.references.filterIsInstance<LayoutPathReference>().firstOrNull() ?: return

                if (reference.path.isEmpty()) return reportMissingPath()

                // We can't resolve relative paths without knowing the project's group, which in some rare cases may
                // not be available until next Gradle sync (e.g. IJ loads a cached version of the model from a previous
                // version of the plugin). In that case, give the user the benefit of the doubt rather than flagging
                // paths that are probably fine. Once a gradle sync happens, we'll try again.
                if (reference.path.startsWith('.') && holder.file.findKobwebProject()?.group == null) return

                if (reference.resolveLayout() == null) {
                    holder.registerProblem(
                        pathExpr,
                        // Highlight the whole path (the reference itself only covers the layout's name)
                        reference.pathRange,
                        UxGlobals.textBundle.message(
                            "inspection.invalid.layout.path.not.found.display.text",
                            reference.path,
                        ),
                        ChooseLayoutQuickFix(),
                    )
                }
            }
        }
    }
}
