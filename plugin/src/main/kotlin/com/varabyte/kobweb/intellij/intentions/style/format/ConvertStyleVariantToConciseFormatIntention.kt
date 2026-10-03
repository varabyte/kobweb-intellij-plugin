package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.style.ADD_VARIANT_BASE_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_RELAXED_STYLE_VARIANT_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

class ConvertStyleVariantToConciseFormatIntention : FormatStyleSheetSingletonBaseIntention(DERIVED_RELAXED_STYLE_VARIANT_KEY) {
    override fun getText() = "Convert to concise CssStyleVariant format"

    // It might seem opposite, but we accept *relaxed* blocks (so we can convert them to concise)
    override fun acceptCssBlock(cssStyleBlock: StyleSheetBlock.Style) =
        // Do not allow compressing a CssStyle variant block that has pseudo-selectors already declared, e.g. `hover`, `focus`
        cssStyleBlock.type == StyleSheetBlock.Style.Type.VARIANT && cssStyleBlock is StyleSheetBlock.Style.Relaxed && cssStyleBlock.otherCalls.isEmpty()


    override val imports: List<FqName> = listOf(ADD_VARIANT_BASE_CALLABLE_ID.asSingleFqName())
    override fun StyleSheetBlock.Style.createReplacementCode(): String {
        // Transform: `CssStyle.addVariant(...) { base { ... } }` to `CssStyle.addVariantBase(...) { ... }`
        val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
        val bodyStr = bodyText.orEmpty()

        return """
            $rootName.${ADD_VARIANT_BASE_CALLABLE_ID.callableName.asString()}$argStr {
                $bodyStr
            }
            """.trimIndent()
    }
}