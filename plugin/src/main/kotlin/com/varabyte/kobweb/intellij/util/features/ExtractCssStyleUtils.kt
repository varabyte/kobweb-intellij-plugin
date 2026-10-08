package com.varabyte.kobweb.intellij.util.features

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.util.kobweb.compose.COMPOSABLE_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierCompanion
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import com.varabyte.kobweb.intellij.util.psi.getEntireDotQualifiedExpression
import com.varabyte.kobweb.intellij.util.psi.getRootReceiverExpression
import com.varabyte.kobweb.intellij.util.psi.isAnnotatedWith
import com.varabyte.kobweb.intellij.wizards.style.ExtractCssStyleWizard
import com.varabyte.kobweb.intellij.wizards.style.performRefactoring
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

object ExtractCssStyleUtils {
    /**
     * Convert [element] to a [KtDotQualifiedExpression] representing a `Modifier` chain.
     *
     * @param allowAnyElementInChain If true, any element inside the modifier chain can be specified. Otherwise, _only_
     *   the initial `Modifier` element is allowed.
     */
    fun toInlineModifierChain(element: PsiElement, allowAnyElementInChain: Boolean = false): KtDotQualifiedExpression? {
        val element = if (element is LeafPsiElement) element.parent else element

        // Get the element as the first item of a modifier chain
        val modifierChainStart = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java)
            ?.getEntireDotQualifiedExpression()
            ?: return null

        run {
            fun PsiElement.isInsideComposableFunction(): Boolean {
                val enclosingFunction = PsiTreeUtil.getParentOfType(this, KtNamedFunction::class.java) ?: return false
                return enclosingFunction.isAnnotatedWith(COMPOSABLE_CLASS_ID)
            }
            fun PsiElement.isInsideComposableLambda(): Boolean {
                val enclosingLambda = PsiTreeUtil.getParentOfType(this, KtLambdaExpression::class.java) ?: return false
                val callExpr = PsiTreeUtil.getParentOfType(enclosingLambda, KtCallExpression::class.java) ?: return false

                // Find the callback parameter associated with this lambda and see if it is Composable
                analyze(enclosingLambda) {
                    val functionCall = callExpr.resolveToCall()
                        ?.successfulFunctionCallOrNull()
                        ?: return false

                    val paramSymbol = functionCall.argumentMapping[enclosingLambda]?.symbol ?: return false
                    val paramType = paramSymbol.returnType as? KaFunctionType ?: return false
                    if (paramType.annotations.contains(COMPOSABLE_CLASS_ID)) return true

                    // If this lambda is not composable but is inline (like `run { ... }`, then it can inherit its
                    // parent's Composable-ness.
                    if (paramSymbol.isNoinline) return false // Inline disabled for this callback
                    val paramOwningFunction = PsiTreeUtil.getParentOfType(paramSymbol.psi, KtNamedFunction::class.java) ?: return false
                    if (paramOwningFunction.hasModifier(KtTokens.INLINE_KEYWORD)) {
                        return enclosingLambda.isInsideComposableLambda()
                    }

                    return false
                }
            }

            if (!(modifierChainStart.isInsideComposableFunction() || modifierChainStart.isInsideComposableLambda())) {
                return null
            }
        }

        val namedExpression = modifierChainStart.getRootReceiverExpression() as? KtNameReferenceExpression ?: return null
        if (!allowAnyElementInChain && element != namedExpression) return null

        // We've done quick early abort checks so far -- let's do a type safe check to really make sure
        analyze(namedExpression) {
            if (!namedExpression.isModifierCompanion()) return null

            // An inline modifier chain exists in the user's normal code; ignore modifier chains declared inside
            // special CssStyle, CssStyleVariant, and Keyframes classes.
            if (StyleSheetBlock.containing(namedExpression) != null) return null
        }

        return modifierChainStart
    }

    fun handleExtractCssStyle(editor: Editor, modifierChainStart: KtDotQualifiedExpression) {
        val project = modifierChainStart.project
        val wizard = ExtractCssStyleWizard(project, ExtractCssStyleWizard.Input(modifierChainStart))
        val wizardResult = wizard.show() ?: return
        wizardResult.performRefactoring(editor, modifierChainStart)
    }
}
