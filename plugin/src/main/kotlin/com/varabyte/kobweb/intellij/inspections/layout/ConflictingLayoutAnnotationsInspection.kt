package com.varabyte.kobweb.intellij.inspections.layout

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.layout.RemoveLayoutQuickFix
import com.varabyte.kobweb.intellij.quickfixes.layout.RemoveNoLayoutQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.NO_LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.PAGE_FQN
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports pages tagged with both `@Layout` and `@NoLayout`, which Kobweb rejects at build time.
 */
class ConflictingLayoutAnnotationsInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                val annotationEntries = function.annotationEntries
                // Kobweb ignores `@NoLayout` on anything but pages
                if (annotationEntries.none { it.isAnnotationOfType(PAGE_FQN) }) return
                val noLayoutAnnotation =
                    annotationEntries.firstOrNull { it.isAnnotationOfType(NO_LAYOUT_FQN) } ?: return
                val layoutAnnotation = annotationEntries.firstOrNull { it.isAnnotationOfType(LAYOUT_FQN) } ?: return

                // A `@Layout` without a path also means "no layout" to Kobweb, so it doesn't conflict (though
                // `InvalidLayoutPathInspection` reports it for other reasons)
                val pathExpr = layoutAnnotation.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
                if (pathExpr is KtStringTemplateExpression && ElementManipulators.getValueText(pathExpr).isBlank()) {
                    return
                }

                // Report both annotations, so the conflict is visible whichever one the user is looking at
                for (annotationEntry in listOf(layoutAnnotation, noLayoutAnnotation)) {
                    holder.registerProblem(
                        annotationEntry,
                        UxGlobals.textBundle.message("inspection.conflicting.layout.annotations.display.text"),
                        RemoveLayoutQuickFix(),
                        RemoveNoLayoutQuickFix(),
                    )
                }
            }
        }
    }
}
