@file:Suppress("UnstableApiUsage")

package com.varabyte.kobweb.intellij.intentions.layout

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.addNoLayoutAnnotation
import com.varabyte.kobweb.intellij.util.kobweb.layout.findDefaultLayout
import com.varabyte.kobweb.intellij.util.psi.isInBody
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Opt a page out of the default layout it uses, by annotating it with `@NoLayout`.
 *
 * This is available from anywhere in the page's header (e.g. its annotations or name), but not its body.
 */
class OptOutOfDefaultLayoutIntention : PsiUpdateModCommandAction<KtNamedFunction>(KtNamedFunction::class.java) {
    override fun getFamilyName() = UxGlobals.textBundle.message("intentions.opt_out_of_default_layout.name")

    override fun getPresentation(context: ActionContext, element: KtNamedFunction): Presentation? {
        if (!element.isInWritableKobwebProject()) return null
        if (element.isInBody(context.offset())) return null
        if (element.findDefaultLayout() == null) return null
        return Presentation.of(familyName)
    }

    override fun invoke(context: ActionContext, element: KtNamedFunction, updater: ModPsiUpdater) {
        element.addNoLayoutAnnotation()
    }
}
