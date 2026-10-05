package com.varabyte.kobweb.intellij.inspections.modifier

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.SafeDeleteExpressionQuickFix
import com.varabyte.kobweb.intellij.quickfixes.style.WrapModifierInBaseBlockQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isDeclaredInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.resolvesToModifier
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

class DanglingModifierInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        if (!holder.file.isDeclaredInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {

            override fun visitBlockExpression(expression: KtBlockExpression) {
                super.visitBlockExpression(expression)

                val statements = expression.statements.takeIf { it.isNotEmpty() } ?: return

                // Only analyze expressions that sit directly inside a KtBlockExpression
                // This automatically skips property initializers, function arguments, explicit returns, etc.
                analyze(expression) {
                    for ((index, statement) in statements.withIndex()) {
                        val isLastStatement = index == statements.lastIndex
                        if (isLastStatement && statement.expectedType.isModifierType()) continue

                        if (statement.resolvesToModifier()) {
                            val insideStyleBlockWithoutBaseBlock =
                                (StyleSheetBlock.Style.containing(statement)?.takeIf { it.baseCall == null } != null)

                            val quickFixes: List<LocalQuickFix> = buildList {
                                if (insideStyleBlockWithoutBaseBlock) {
                                    add(WrapModifierInBaseBlockQuickFix())
                                }
                                add(SafeDeleteExpressionQuickFix("dangling modifier chain"))
                            }
                            holder.registerProblem(
                                statement,
                                "This modifier chains is dangling. You should instead use it or remove it.",
                                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                *quickFixes.toTypedArray()
                            )

                        }
                    }
                }
            }
        }
    }
}