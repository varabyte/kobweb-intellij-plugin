package com.varabyte.kobweb.intellij.util.features

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.varabyte.kobweb.intellij.util.compose.COMPOSABLE_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierCompanion
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import com.varabyte.kobweb.intellij.util.psi.getEntireDotQualifiedExpression
import com.varabyte.kobweb.intellij.util.psi.getRootReceiverExpression
import com.varabyte.kobweb.intellij.util.psi.isAnnotatedWith
import com.varabyte.kobweb.intellij.wizards.style.ExtractCssStyleWizard
import com.varabyte.kobweb.intellij.wizards.style.performRefactoring
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
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
            fun PsiElement.isInComposableContext(): Boolean {
                // We intentionally stop at the first method context. For example, if we're in a non-composable lambda
                // block inside a composable function, we are essentially in a non-composable context then (unless the
                // lambda is inlined, in which case it inherits the Composable-ness of the parent).
                val enclosing = PsiTreeUtil.getParentOfType(this, KtNamedFunction::class.java, KtLambdaExpression::class.java)
                    ?: return false

                return when (enclosing) {
                    is KtNamedFunction -> analyze(enclosing) { enclosing.isAnnotatedWith(COMPOSABLE_CLASS_ID) }
                    is KtLambdaExpression -> {
                        // If we're in a lambda, resolve the signature of the lambda parameter we are associated with,
                        // as that's what we'll need to check for Composable-ness. For example, something like
                        // `block: @Composable () -> Unit`
                        val callExpr = PsiTreeUtil.getParentOfType(enclosing, KtCallExpression::class.java) ?: return false

                        val inheritsParentContext = analyze(enclosing) {
                            val functionCall = callExpr.resolveToCall()?.successfulFunctionCallOrNull() ?: return false
                            val paramSymbol = functionCall.argumentMapping[enclosing]?.symbol ?: return false
                            val paramType = paramSymbol.returnType as? KaFunctionType ?: return false
                            if (paramType.annotations.contains(COMPOSABLE_CLASS_ID)) return true

                            // If this lambda is not composable but is inlined (like `run { ... }`), then it inherits the
                            // Composable-ness of wherever it is called from.
                            !paramSymbol.isNoinline && (functionCall.symbol as? KaNamedFunctionSymbol)?.isInline == true
                        }

                        // Keep climbing, starting from the lambda, to the next function or lambda boundary
                        inheritsParentContext && enclosing.isInComposableContext()
                    }
                    else -> false
                }
            }

            if (!modifierChainStart.isInComposableContext()) {
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
        val containingFun = modifierChainStart.parentOfType<KtNamedFunction>()
        val wizard = ExtractCssStyleWizard(project, ExtractCssStyleWizard.Input(
            modifierChainStart,
            initialPrefix = containingFun?.name
        ))
        val wizardResult = wizard.show() ?: return
        wizardResult.performRefactoring(editor, modifierChainStart)
    }
}
