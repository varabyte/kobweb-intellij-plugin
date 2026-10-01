package com.varabyte.kobweb.intellij.intentions

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.style.CssStyleBlock
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_RELAXED_CSS_STYLE_BASE_NAME_REFERENCE_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

class ConvertToConciseCssStyleFormatIntention : CssStyleFormatBaseIntention(DERIVED_RELAXED_CSS_STYLE_BASE_NAME_REFERENCE_KEY) {
    override fun getText() = "Convert to concise CssStyle format"

    // It might seem opposite, but we accept *relaxed* blocks (so we can convert them to concise)
    override fun acceptCssBlock(cssStyleBlock: CssStyleBlock) =
        // Do not allow compressing a CssStyle block that has psuedo-selectors already declared, e.g. `hover`, `focus`
        cssStyleBlock is CssStyleBlock.Relaxed && cssStyleBlock.otherCalls.isEmpty()

    override fun CssStyleBlock.createReplacementCode(): String {
        // Transform: `CssStyle(...) { base { ... } }` to `CssStyle.base(...) { ... }`
        val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
        val bodyStr = bodyText.orEmpty()

        return """
            CssStyle.base$argStr {
                $bodyStr
            }
            """.trimIndent()
    }
}