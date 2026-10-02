package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_RELAXED_CSS_STYLE_BASE_NAME_REFERENCE_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

class ConvertToConciseCssStyleFormatIntention : CssStyleFormatBaseIntention(DERIVED_RELAXED_CSS_STYLE_BASE_NAME_REFERENCE_KEY) {
    override fun getText() = "Convert to concise CssStyle format"

    // It might seem opposite, but we accept *relaxed* blocks (so we can convert them to concise)
    override fun acceptCssBlock(cssStyleBlock: StyleSheetBlock.Style) =
        // Do not allow compressing a CssStyle block that has pseudo-selectors already declared, e.g. `hover`, `focus`
        cssStyleBlock.type == StyleSheetBlock.Style.Type.DEFINITION && cssStyleBlock is StyleSheetBlock.Style.Relaxed && cssStyleBlock.otherCalls.isEmpty()


    override fun StyleSheetBlock.Style.createReplacementCode(): String {
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