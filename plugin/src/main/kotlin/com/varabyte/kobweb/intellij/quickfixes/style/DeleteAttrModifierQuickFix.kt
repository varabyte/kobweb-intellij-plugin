package com.varabyte.kobweb.intellij.quickfixes.style

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.varabyte.kobweb.intellij.inspections.style.AttributeModifierInStyleSheetBlockInspection
import com.varabyte.kobweb.intellij.util.psi.deleteFromCallChain
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * A quick fix to help move attribute modifiers out of the core of CssStyle blocks.
 *
 * See also: [AttributeModifierInStyleSheetBlockInspection].
 */
class DeleteAttrModifierQuickFix(private val attrModifierName: String) : KotlinModCommandQuickFix<KtCallExpression>(), LowPriorityAction {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.delete_attr_modifier.name")
    override fun getName() = UxGlobals.textBundle.message("quickfixes.delete_attr_modifier.text", attrModifierName)

    override fun applyFix(
        project: Project,
        element: KtCallExpression,
        updater: ModPsiUpdater
    ) {
        element.deleteFromCallChain()
    }
}