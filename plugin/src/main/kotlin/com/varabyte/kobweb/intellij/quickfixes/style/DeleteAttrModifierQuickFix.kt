package com.varabyte.kobweb.intellij.quickfixes.style

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.varabyte.kobweb.intellij.inspections.style.AttributeModifierInStyleSheetBlockInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

/**
 * A quick fix to help move attribute modifiers out of the core of CssStyle blocks.
 *
 * See also: [AttributeModifierInStyleSheetBlockInspection].
 */
class DeleteAttrModifierQuickFix(private val attrModifierName: String) : KotlinModCommandQuickFix<KtCallExpression>(), LowPriorityAction {
    companion object {
        fun removeCallExpression(callExpression: KtCallExpression) {
            // Delete the original misplaced property. This should ALWAYS be a part of a KtDotQualifiedExpression (e.g.,
            // `Modifier.a().b().yourAttrModifier().c().d()`, because attribute modifiers are always part of a modifier
            // chain. The following approach not only deletes the attribute modifier call, but it ALSO deletes the `.` before
            // it.
            val parent = callExpression.parent
            if (parent is KtDotQualifiedExpression && parent.selectorExpression == callExpression) {
                parent.replace(parent.receiverExpression)
            } else {
                // My understanding is we will never hit this branch! But just in case my above assumption is somehow not
                // correct, at least we can delete the problematic modifier anyway. The user can manually clean up any
                // remaining issues with the code, if there are any.
                callExpression.delete()
            }
        }
    }

    override fun getFamilyName() = "Delete attribute modifiers from their CssStyle blocks."
    override fun getName() = "Delete the '$attrModifierName' attribute modifier from this CssStyle block."

    override fun applyFix(
        project: Project,
        element: KtCallExpression,
        updater: ModPsiUpdater
    ) {
        removeCallExpression(element)
    }
}