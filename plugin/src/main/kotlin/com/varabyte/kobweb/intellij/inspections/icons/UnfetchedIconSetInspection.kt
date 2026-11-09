package com.varabyte.kobweb.intellij.inspections.icons

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.icons.FetchIconsQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.icons.findIconSetId
import com.varabyte.kobweb.intellij.util.kobweb.icons.findIconsLockFile
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports icon sets in a build script's `icons { ... }` block whose data hasn't been fetched yet.
 */
class UnfetchedIconSetInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isWritable) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                val id = expression.findIconSetId() ?: return
                if (holder.file.findIconsLockFile(id) != null) return

                holder.registerProblem(
                    expression.calleeExpression ?: return,
                    UxGlobals.textBundle.message("inspection.unfetched.icon.set.display.text", id),
                    FetchIconsQuickFix(),
                )
            }
        }
    }
}
