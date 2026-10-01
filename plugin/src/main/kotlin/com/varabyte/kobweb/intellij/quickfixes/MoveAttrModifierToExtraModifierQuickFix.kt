package com.varabyte.kobweb.intellij.quickfixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.varabyte.kobweb.intellij.inspections.AttributeModifierInCssStyleInspection
import com.varabyte.kobweb.intellij.util.kobweb.style.CssStyleBlock
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * A quick fix to help move attribute modifiers out of the core of CssStyle blocks.
 *
 * See also: [AttributeModifierInCssStyleInspection].
 */
class MoveAttrModifierToExtraModifierQuickFix(private val attrModifierName: String, private val cssStyleBlock: CssStyleBlock) : LocalQuickFix {
    override fun getFamilyName() = "Move attribute modifiers to their CssStyle 'extraModifier' arguments."
    override fun getName() = "Move '$attrModifierName' to CssStyle 'extraModifier' argument."

    override fun generatePreview(
        project: Project,
        previewDescriptor: ProblemDescriptor
    ): IntentionPreviewInfo {
        val previewStr = when (cssStyleBlock) {
            is CssStyleBlock.Concise ->
                """
                    Cssstyle.base(
                        extraModifier = { Modifier.$attrModifierName(...) }
                    )
                """.trimIndent()
            is CssStyleBlock.Relaxed ->
                """
                    Cssstyle(extraModifier = {
                        Modifier.$attrModifierName(...)
                    }) {
                        base { ... }
                    }
                """.trimIndent()
        }

        return IntentionPreviewInfo.Html("<pre><code>$previewStr</code></pre>")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val callExpression = descriptor.psiElement as? KtCallExpression
            ?: return

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
            val targetCallExpression = when (cssStyleBlock) {
                // Change `CssStyle.base { ... }` to `CssStyle.base(extraModifier = { Modifier... })` n.text} }) { ... }"
                is CssStyleBlock.Concise -> cssStyleBlock.baseCall
                is CssStyleBlock.Relaxed -> cssStyleBlock.rootExpression
            }

            val extraModifierArg = psiFactory.createArgument("extraModifier = { Modifier.${callExpression.text} }")
            val callArgument = psiFactory.createCallArguments("(${extraModifierArg.text})")
            targetCallExpression.addAfter(callArgument, targetCallExpression.calleeExpression)
        }

        // At this point we've made a copy of the original attribute modifier, so it is now safe to delete it.
        val deleteFix = DeleteAttrModifierQuickFix(attrModifierName)
        deleteFix.applyFix(project, descriptor)
    }
}