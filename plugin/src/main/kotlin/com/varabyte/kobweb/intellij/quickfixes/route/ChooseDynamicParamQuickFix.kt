package com.varabyte.kobweb.intellij.quickfixes.route

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators
import com.intellij.util.text.EditDistance
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteParamKey
import com.varabyte.kobweb.intellij.util.kobweb.route.isReadWithGetValue
import com.varabyte.kobweb.intellij.util.kobweb.route.isValueLookup
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Fix an unknown key into a route's dynamic params by choosing one of its dynamic segments' names from a popup, e.g.
 * "post" for `ctx.route.dynamicParams["pots"]` in a page registered at "/blog/{post}".
 *
 * Names that best match the current key are listed first, so a typo'd key puts the intended name at the top.
 *
 * As the chosen name is always in the route's dynamic params, a lookup of its value is also changed to `getValue`, e.g.
 * `dynamicParams["pots"]` into `dynamicParams.getValue("post")`.
 *
 * @param names The names of the route's dynamic segments.
 */
class ChooseDynamicParamQuickFix(private val names: List<String>) : ModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.choose_dynamic_param.name")

    override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
        val key = descriptor.psiElement as? KtStringTemplateExpression ?: return ModCommand.nop()
        val currentName = ElementManipulators.getValueText(key)
        val sortedNames = names.sortedBy { name -> EditDistance.levenshtein(currentName, name, false) }

        return ModCommand.chooseAction(
            UxGlobals.textBundle.message("popups.dynamic.param.chooser.title"),
            sortedNames.map { name -> SetKeyAction(key, name) },
        )
    }

    @Suppress("UnstableApiUsage")
    private class SetKeyAction(
        key: KtStringTemplateExpression,
        private val name: String,
    ) : PsiUpdateModCommandAction<KtStringTemplateExpression>(key) {
        override fun getFamilyName() = UxGlobals.textBundle.message("popups.dynamic.param.chooser.set.key.command")

        override fun getPresentation(context: ActionContext, element: KtStringTemplateExpression) =
            Presentation.of(name)

        override fun invoke(context: ActionContext, element: KtStringTemplateExpression, updater: ModPsiUpdater) {
            val key = ElementManipulators.handleContentChange(element, name) ?: return
            // The chosen name is always in the route's dynamic params, so its value can be read with getValue
            val paramKey = RouteParamKey.from(key) ?: return
            if (paramKey.isValueLookup && !paramKey.isReadWithGetValue) paramKey.replaceWithDynamicParamsGetValue()
        }
    }
}
