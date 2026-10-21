package com.varabyte.kobweb.intellij.intentions.layout

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.varabyte.kobweb.intellij.references.layout.LayoutPathReference
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.LayoutPathChooser
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Change a `@Layout` annotation's layout by choosing a new one from a popup.
 *
 * This is only available for valid paths. Invalid ones are already handled by
 * [com.varabyte.kobweb.intellij.quickfixes.layout.ChooseLayoutQuickFix], and we don't want both to show up.
 *
 * @see LayoutPathChooser
 */
class ChangeLayoutIntention :
    PsiBasedModCommandAction<KtStringTemplateExpression>(KtStringTemplateExpression::class.java) {
    override fun getFamilyName() = UxGlobals.textBundle.message("intentions.change_layout.name")

    override fun getPresentation(context: ActionContext, element: KtStringTemplateExpression): Presentation? {
        if (!element.isInWritableKobwebProject()) return null
        val reference = element.references.filterIsInstance<LayoutPathReference>().firstOrNull() ?: return null
        if (reference.value.isEmpty() || reference.resolveLayout() == null) return null
        return Presentation.of(familyName)
    }

    override fun perform(context: ActionContext, element: KtStringTemplateExpression): ModCommand {
        return LayoutPathChooser.choose(element)
    }
}
