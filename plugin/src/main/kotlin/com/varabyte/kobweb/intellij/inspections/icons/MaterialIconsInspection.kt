package com.varabyte.kobweb.intellij.inspections.icons

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.util.kobweb.icons.findIconSetId
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Suggests Material Symbols (`ms()`) over Material Icons (`mdi()`) in a build script's `icons { ... }` block, since
 * Google has deprecated Material Icons in favor of Material Symbols.
 */
class MaterialIconsInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isWritable) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (expression.findIconSetId() != "mdi") return

                holder.registerProblem(
                    expression.calleeExpression ?: return,
                    UxGlobals.textBundle.message("inspection.material.icons.display.text"),
                )
            }
        }
    }
}
