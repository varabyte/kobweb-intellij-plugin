package com.varabyte.kobweb.intellij.intentions

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.style.CssStyleBlock
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_CONCISE_CSS_STYLE_BASE_NAME_REFERENCE_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

class ConvertToRelaxedCssStyleFormatIntention : CssStyleFormatBaseIntention(DERIVED_CONCISE_CSS_STYLE_BASE_NAME_REFERENCE_KEY) {
    override fun getText() = "Convert to relaxed CssStyle format"

    // It might seem opposite, but we accept *concise* blocks (so we can convert them to relaxed)
    override fun acceptCssBlock(cssStyleBlock: CssStyleBlock) = cssStyleBlock is CssStyleBlock.Concise

    override fun CssStyleBlock.createReplacementCode(): String {
        // Transform: `CssStyle.base(...) { ... }` to `CssStyle(...) { base { ... } }`
        val argListStr = extraModifierArgs?.text.orEmpty()
        val bodyStr = bodyText.orEmpty()

        return """
            CssStyle$argListStr {
                base {
                    $bodyStr
                }
            }
            """.trimIndent()
    }
}