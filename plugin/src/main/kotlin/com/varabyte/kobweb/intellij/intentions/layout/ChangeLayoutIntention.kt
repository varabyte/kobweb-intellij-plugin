package com.varabyte.kobweb.intellij.intentions.layout

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.varabyte.kobweb.intellij.references.layout.LayoutPathReference
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.LayoutPathChooser
import com.varabyte.kobweb.intellij.util.kobweb.layout.findDefaultLayout
import com.varabyte.kobweb.intellij.util.psi.isInBody
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Change a page's layout by choosing a new one from a popup.
 *
 * This is available on:
 * - a valid `@Layout` path string, which is replaced with the chosen one. (Invalid paths are already handled by
 *   [com.varabyte.kobweb.intellij.quickfixes.layout.ChooseLayoutQuickFix], and we don't want both to show up.)
 * - the function header (e.g. the annotations or name) of a page that uses a default layout, which gets a `@Layout`
 *   annotation with the chosen one.
 *
 * @see LayoutPathChooser
 */
class ChangeLayoutIntention : PsiBasedModCommandAction<KtElement>(KtElement::class.java) {
    override fun getFamilyName() = UxGlobals.textBundle.message("intentions.change_layout.name")

    override fun isElementApplicable(element: KtElement, context: ActionContext): Boolean {
        return when (element) {
            is KtStringTemplateExpression -> {
                val reference = element.references.filterIsInstance<LayoutPathReference>().firstOrNull()
                reference != null && reference.path.isNotEmpty() && reference.resolveLayout() != null
            }

            is KtNamedFunction -> !element.isInBody(context.offset()) && element.findDefaultLayout() != null
            else -> false
        }
    }

    override fun getPresentation(context: ActionContext, element: KtElement): Presentation? {
        if (!element.isInWritableKobwebProject()) return null
        return Presentation.of(familyName)
    }

    override fun perform(context: ActionContext, element: KtElement): ModCommand {
        return LayoutPathChooser.choose(element)
    }
}
