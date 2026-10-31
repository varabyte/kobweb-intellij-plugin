package com.varabyte.kobweb.intellij.inspections.modifier

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.SafeDeleteExpressionQuickFix
import com.varabyte.kobweb.intellij.quickfixes.style.WrapModifierInBaseBlockQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.resolvesToModifier
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtVisitorVoid

class DanglingModifierInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {

            override fun visitBlockExpression(expression: KtBlockExpression) {
                super.visitBlockExpression(expression)

                val statements = expression.statements.takeIf { it.isNotEmpty() } ?: return

                // Only analyze expressions that sit directly inside a KtBlockExpression
                // This automatically skips property initializers, function arguments, explicit returns, etc.
                analyze(expression) {
                    for ((index, statement) in statements.withIndex()) {
                        val isLastStatement = index == statements.lastIndex
                        if (isLastStatement) {
                            // The last statement is used if it's the block's value, e.g. a lambda's return value or
                            // the value of an `if` or `when` branch, which its expected type tells us. However, the
                            // last statement of an annotated lambda (e.g. `@Composable { ... }`) has no expected type,
                            // probably due to a Kotlin bug, so in that case, check what the lambda returns directly.
                            // See also: https://youtrack.jetbrains.com/issue/KTIJ-40730
                            val expectedType = statement.expectedType
                            if (expectedType?.isModifierType() ?: expression.isBodyOfLambdaReturningModifier()) continue
                        }

                        if (statement.resolvesToModifier()) {
                            val insideStyleBlockWithoutBaseBlock =
                                (StyleSheetBlock.Style.containing(statement)?.takeIf { it.baseCall == null } != null)

                            val quickFixes: List<LocalQuickFix> = buildList {
                                if (insideStyleBlockWithoutBaseBlock) {
                                    add(WrapModifierInBaseBlockQuickFix())
                                }
                                add(SafeDeleteExpressionQuickFix("dangling modifier"))
                            }
                            holder.registerProblem(
                                statement,
                                UxGlobals.textBundle.message("inspection.dangling.modifier.display.text"),
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

/**
 * Returns true if this block is the body of a lambda that returns a `Modifier`, in which case its last statement is
 * that return value.
 */
context(kaSession: KaSession)
private fun KtBlockExpression.isBodyOfLambdaReturningModifier(): Boolean = with(kaSession) {
    val functionLiteral = parent as? KtFunctionLiteral ?: return@with false
    functionLiteral.symbol.returnType.isModifierType()
}