package com.varabyte.kobweb.intellij.inspections.worker

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.ReplaceCalleeQuickFix
import com.varabyte.kobweb.intellij.util.compose.REMEMBER_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.worker.REMEMBER_WORKER_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.worker.WORKER_CLASS_ID
import com.varabyte.kobweb.intellij.util.psi.resolveToCallableId
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

class RememberWorkerInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        val ktFile = holder.file as? KtFile ?: return KtVisitorVoid.EMPTY_VISITOR
        if (!ktFile.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        fun KtFile.rememberNames(): Set<String> {
            val names = mutableSetOf(REMEMBER_CALLABLE_ID.callableName.asString())
            importDirectives.asSequence()
                .filter { it.importedFqName == REMEMBER_CALLABLE_ID.asSingleFqName() }
                .mapNotNull { it.aliasName }
                .forEach { names += it }

            return names
        }
        val rememberNames = ktFile.rememberNames()

        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                // First, do some early aborts to avoid wasting time on extra analysis computation

                // `remember` always takes a lambda -- quickly filter out all non-lambda method calls
                val lambda = expression.lambdaArguments.lastOrNull()?.getLambdaExpression() ?: return

                val calleeExpr = expression.calleeExpression as? KtNameReferenceExpression ?: return
                if (calleeExpr.getReferencedName() !in rememberNames) return

                val returnsWorker = analyze(expression) {
                    expression.resolveToCallableId()?.takeIf { it == REMEMBER_CALLABLE_ID } ?: return

                    fun KaType.isWorker() = this is KaClassType && classId == WORKER_CLASS_ID
                    val blockReturnType = (lambda.expressionType as? KaFunctionType)?.returnType ?: return
                    blockReturnType.isWorker() || blockReturnType.allSupertypes.any { it.isWorker() }
                }

                if (returnsWorker) {
                    holder.registerProblem(
                        calleeExpr,
                        "Workers wrapped in `${REMEMBER_CALLABLE_ID.callableName.asString()}` should instead be wrapped in `${REMEMBER_WORKER_CALLABLE_ID.callableName.asString()}`.",
                        ReplaceCalleeQuickFix(REMEMBER_WORKER_CALLABLE_ID.asSingleFqName())
                    )
                }
            }
        }
    }
}