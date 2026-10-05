package com.varabyte.kobweb.intellij.quickfixes

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtExpression

/**
 * A quick fix to remove some target expression.
 */
class SafeDeleteExpressionQuickFix(private val text: String) : KotlinModCommandQuickFix<KtExpression>(), LowPriorityAction {
    override fun getFamilyName() = "Safe delete $text(s)"
    override fun getName() = "Safe delete the $text"

    override fun applyFix(
        project: Project,
        element: KtExpression,
        updater: ModPsiUpdater
    ) {
        element.delete()
    }
}