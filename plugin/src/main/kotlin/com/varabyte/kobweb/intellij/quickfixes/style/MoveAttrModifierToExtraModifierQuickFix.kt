package com.varabyte.kobweb.intellij.quickfixes.style

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.varabyte.kobweb.intellij.inspections.style.AttributeModifierInStyleSheetBlockInspection
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * A quick fix to help move attribute modifiers out of the core of CssStyle blocks.
 *
 * See also: [AttributeModifierInStyleSheetBlockInspection].
 */
class MoveAttrModifierToExtraModifierQuickFix(private val attrModifierName: String) : KotlinModCommandQuickFix<KtCallExpression>() {
    override fun getFamilyName() = "Move attribute modifiers to their CssStyle 'extraModifier' arguments."
    override fun getName() = "Move '$attrModifierName' to CssStyle 'extraModifier' argument."

    override fun applyFix(
        project: Project,
        element: KtCallExpression,
        updater: ModPsiUpdater
    ) {
        val callExpression = element // for readability

        val cssStyleBlock = analyze(callExpression) {
            StyleSheetBlock.Style.containing(callExpression)
        } ?: return

        val psiFactory = KtPsiFactory(project)

        cssStyleBlock.extraModifierArg?.let { extraModifierArg ->
            // There are two overloads, one which returns a lambda that produces a Modifier chain and convenience one
            // which is set to a modifier chain directly.
            val argExpression = extraModifierArg.getArgumentExpression() ?: return

            val modifierChainExpr = when (argExpression) {
                is KtLambdaExpression -> argExpression.bodyExpression?.statements?.lastOrNull() ?: return
                else -> argExpression
            }
            modifierChainExpr.replace(psiFactory.createExpression("${modifierChainExpr.text}.${callExpression.text}"))
        } ?: run {
            // If here, no extraModifier arg exists yet.
            val targetCallExpression = cssStyleBlock.extraModifierFunc

            val extraModifierArg = psiFactory.createArgument("extraModifier = { Modifier.${callExpression.text} }")
            val callArgument = psiFactory.createCallArguments("(${extraModifierArg.text})")
            targetCallExpression.addAfter(callArgument, targetCallExpression.calleeExpression)
        }

        // At this point we've made a copy of the original attribute modifier, so it is now safe to delete it.
        DeleteAttrModifierQuickFix.removeCallExpression(callExpression)
    }
}