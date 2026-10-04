package com.varabyte.kobweb.intellij.inspections.style

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.style.DeleteAttrModifierQuickFix
import com.varabyte.kobweb.intellij.quickfixes.style.MoveAttrModifierToExtraModifierQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isDeclaredInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

class AttributeModifierInStyleSheetBlockInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        if (!holder.file.isDeclaredInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val attrModifierFunction = analyze(expression) {
                    val function = expression.calleeExpression?.mainReference?.resolve() as? KtNamedFunction ?: return
                    if (function.getWebModifierType() != WebModifierType.ATTRS) return
                    function
                }
                val cssStyleBlock = analyze(expression) {
                    StyleSheetBlock.containing(expression)
                        ?.takeUnless { block ->
                            block is StyleSheetBlock.Style &&
                            // One exception: attribute modifiers are allowed inside the extra modifier argument
                            block.extraModifierArg?.anyDescendantOfType<KtCallExpression> { it == expression } == true
                        } ?: return
                }

                val styleExtraModifierSuggestedFix = when (cssStyleBlock) {
                    is StyleSheetBlock.Style.Definition.Concise ->
                        "${cssStyleBlock.rootName}.base(extraModifier = { Modifier.${expression.text} })"
                    is StyleSheetBlock.Style.Definition.Relaxed ->
                        """
                        ${cssStyleBlock.rootName}(extraModifier = { Modifier.${expression.text} }) {
                            base { ... }
                        }
                        """.trimIndent()

                    is StyleSheetBlock.Style.Extended.Concise ->
                        "${cssStyleBlock.rootName}.extendedByBase(extraModifier = { Modifier.${expression.text} })"
                    is StyleSheetBlock.Style.Extended.Relaxed ->
                        """
                        ${cssStyleBlock.rootName}.extendedBy(extraModifier = { Modifier.${expression.text} }) {
                            base { ... }
                        }
                        """.trimIndent()

                    is StyleSheetBlock.Style.Variant.Concise ->
                        "${cssStyleBlock.rootName}.addVariantBase(extraModifier = { Modifier.${expression.text} })"
                    is StyleSheetBlock.Style.Variant.Relaxed ->
                        """
                        ${cssStyleBlock.rootName}.addVariant(extraModifier = { Modifier.${expression.text} }) {
                            base { ... }
                        }
                        """.trimIndent()
                    else -> null // Non-style stylesheet blocks don't
                }

                // name should always be set but use a fallback just in case...
                val attrModifierName = attrModifierFunction.name ?: expression.text
                val applicableQuickFixes = buildList {
                    if (styleExtraModifierSuggestedFix != null) {
                        add(MoveAttrModifierToExtraModifierQuickFix(attrModifierName))
                    }
                    add(DeleteAttrModifierQuickFix(attrModifierName))
                }

                holder.registerProblem(
                    expression,
                    buildString {
                        append("<html>")
                        append("Attribute modifiers are not allowed in stylesheet declarations and will result in an exception when your site runs.")

                        if (styleExtraModifierSuggestedFix != null) {
                            appendLine()
                            appendLine()
                            appendLine("You can move this modifier to the <code>extraModifier</code> argument, like so:")

                            append("<pre><code>")
                            append(styleExtraModifierSuggestedFix)
                            append("</code></pre>")
                            append(' ')
                            append("or you can remove it entirely, perhaps adding it where you convert this style into a modifier using <code>toModifier()</code>.")
                        }

                        append("</html>")
                    },
                    *applicableQuickFixes.toTypedArray()
                )

            }
        }
    }
}