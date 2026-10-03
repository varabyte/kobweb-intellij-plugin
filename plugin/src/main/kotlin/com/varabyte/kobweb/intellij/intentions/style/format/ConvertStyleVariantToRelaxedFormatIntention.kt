package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.style.ADD_VARIANT_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_CONCISE_STYLE_VARIANT_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

class ConvertStyleVariantToRelaxedFormatIntention : FormatStyleSheetSingletonBaseIntention(DERIVED_CONCISE_STYLE_VARIANT_KEY) {
    override fun getText() = "Convert to relaxed CssStyleVariant format"

    // It might seem opposite, but we accept *concise* blocks (so we can convert them to relaxed)
    override fun acceptCssBlock(cssStyleBlock: StyleSheetBlock.Style) =
        cssStyleBlock.type == StyleSheetBlock.Style.Type.VARIANT && cssStyleBlock is StyleSheetBlock.Style.Concise

    override val imports: List<FqName> = listOf(ADD_VARIANT_CALLABLE_ID.asSingleFqName())
    override fun StyleSheetBlock.Style.createReplacementCode(): String {
        // Transform: `CssStyle.base(...) { ... }` to `CssStyle(...) { base { ... } }`
        val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
        val bodyStr = bodyText.orEmpty()

        return """
            $rootName.${ADD_VARIANT_CALLABLE_ID.callableName.asString()}$argStr {
                base {
                    $bodyStr
                }
            }
            """.trimIndent()
    }
}