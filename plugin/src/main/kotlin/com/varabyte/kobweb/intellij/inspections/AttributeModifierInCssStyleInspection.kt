package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.util.kobweb.isDeclaredInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.style.CssStyleBlock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

class AttributeModifierInCssStyleInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        if (!holder.file.isDeclaredInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val cssStyleBlock = analyze(expression) {
                    val function = expression.calleeExpression?.mainReference?.resolve() as? KtNamedFunction ?: return
                    if (function.getWebModifierType() != WebModifierType.ATTRS) return

                    CssStyleBlock.findContaining(expression)
                        ?.takeUnless { block ->
                            // One exception: attribute modifiers are allowed inside the extra modifier argument
                            block.extraModifierArg?.anyDescendantOfType<KtCallExpression> { it == expression } == true
                        }

                        ?: return
                }

                val suggestedFix = when (cssStyleBlock) {
                    is CssStyleBlock.Concise ->
                        "CssStyle.base(extraModifier = { Modifier.${expression.text} })"
                    is CssStyleBlock.Relaxed ->
                        """
                        CssStyle(extraModifier = { Modifier.${expression.text} }) {
                            base { ... }
                        }
                        """.trimIndent()
                }

                holder.registerProblem(
                    expression,
                    buildString {
                        append("<html>")
                        appendLine("Attribute modifiers are not allowed in CssStyle declarations and will result in an exception when your site runs.")
                        appendLine()

                        appendLine("You can move this modifier to the <code>extraModifier</code> argument, like so:")

                        append("<pre><code>")
                        append(suggestedFix)
                        append("</code></pre> ")

                        append("or you can remove it entirely, adding it where you convert this style into a modifier using <code>toModifier()</code>.")

                        append("</html>")
                    }
                )

            }
        }
    }
}