package com.varabyte.kobweb.intellij.annotations

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.ui.JBColor
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierChainingExtension
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

// I just want a simple text effect to apply to attribute modifiers. The "correct" way to do this seems to be to use a
// complex system where we create and initialize text attributes via a color settings page.
// See also: https://plugins.jetbrains.com/docs/intellij/syntax-highlighter-and-color-settings-page.html#register-the-syntax-highlighter-factory
// and: https://plugins.jetbrains.com/docs/intellij/annotator.html
@Suppress("DEPRECATION")
private val ATTR_MODIFIER_KEY = TextAttributesKey.createTextAttributesKey(
    "ATTR_MODIFIER",
    TextAttributes().apply {
        this.effectColor = JBColor.foreground()
        this.effectType = EffectType.BOLD_DOTTED_LINE
    }
)

/**
 * Convert a call expression (which is text that is function-shaped) to an actual backing function.
 */
private fun KtCallExpression.resolveToKtNamedFunction(): KtNamedFunction? {
    val refExpr = calleeExpression as? KtNameReferenceExpression ?: return null
    return analyze(refExpr) {
        val symbol = refExpr.mainReference.resolveToSymbol() as? KaNamedFunctionSymbol
        symbol?.psi as? KtNamedFunction
    }
}

class AttrModifierFunctionAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val callExpression = element as? KtCallExpression ?: return
        val function = element.resolveToKtNamedFunction() ?: return
        analyze(function) {
            if (!function.isModifierChainingExtension()) return
        }
        val modifierType = function.getWebModifierType()
        if (modifierType == WebModifierType.ATTRS) {
            // Limit syntax highlighting to the name of the function being annotated
            val targetElementName = callExpression.calleeExpression as? KtNameReferenceExpression ?: return
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(targetElementName)
                .textAttributes(ATTR_MODIFIER_KEY)
                .create()
        }
    }
}