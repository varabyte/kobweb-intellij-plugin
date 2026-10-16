package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.style.ADD_VARIANT_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_CONCISE_STYLE_VARIANT_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

// It might seem opposite, but we accept *concise* blocks (so we can convert them to expanded)
class ConvertStyleVariantToExpandedFormatIntention : FormatStyleSheetSingletonBaseIntention<StyleSheetBlock.Style.Variant.Definition.Concise>(
    DERIVED_CONCISE_STYLE_VARIANT_KEY,
    UxGlobals.textBundle.message("intentions.convert_style_variant_to_expanded_format.name"),
    castStyleBlock = { (it as? StyleSheetBlock.Style.Variant.Definition.Concise) }
) {
    override val imports: List<FqName> = listOf(ADD_VARIANT_CALLABLE_ID.asSingleFqName())
    override fun StyleSheetBlock.Style.Variant.Definition.Concise.createReplacementCode(): String {
        // Transform: `CssStyle.base(...) { ... }` to `CssStyle(...) { base { ... } }`
        val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
        val bodyStr = baseBodyText.orEmpty()

        return """
            $rootName.${ADD_VARIANT_CALLABLE_ID.callableName.asString()}$argStr {
                base {
                    $bodyStr
                }
            }
            """.trimIndent()
    }
}