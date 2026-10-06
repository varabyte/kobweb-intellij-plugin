package com.varabyte.kobweb.intellij.util.features

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierCompanion
import com.varabyte.kobweb.intellij.util.kobweb.silk.INIT_SILK_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import com.varabyte.kobweb.intellij.util.psi.getEntireDotQualifiedExpression
import com.varabyte.kobweb.intellij.util.psi.getRootReceiverExpression
import com.varabyte.kobweb.intellij.util.psi.isAnnotatedWith
import com.varabyte.kobweb.intellij.wizards.cssstyle.ExtractCssStyleWizard
import com.varabyte.kobweb.intellij.wizards.cssstyle.performRefactoring
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

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

        // If we're inside an `@InitSilk` method, we are at a point where we are still defining / registering styles via
        // modifier chains. This is not the right context to suggest converting them to a CssStyle.
        if (PsiTreeUtil.collectParents(element, KtNamedFunction::class.java, /* includeMyself =*/false) { false }.any {
                    (it as KtNamedFunction).isAnnotatedWith(INIT_SILK_CLASS_ID) })
        {
            return null
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
