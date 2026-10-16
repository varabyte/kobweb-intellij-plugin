package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.modifier.MODIFIER_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.ADD_VARIANT_BASE_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_EXPANDED_STYLE_VARIANT_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

// It might seem opposite, but we accept *expanded* blocks (so we can convert them to concise)
class ConvertStyleVariantToConciseFormatIntention : FormatStyleSheetSingletonBaseIntention<StyleSheetBlock.Style.Variant.Definition.Expanded>(
    DERIVED_EXPANDED_STYLE_VARIANT_KEY,
    UxGlobals.textBundle.message("intentions.convert_style_variant_to_concise_format.name"),
    // Do not allow compressing a CssStyle variant block that has pseudo-selectors already declared, e.g. `hover`, `focus`
    castStyleBlock = { (it as? StyleSheetBlock.Style.Variant.Definition.Expanded)?.takeIf { it.blockIsEmptyOrOnlyContainsBaseBlock() } }
) {
    override val imports: List<FqName> = listOf(
        ADD_VARIANT_BASE_CALLABLE_ID.asSingleFqName(),
        MODIFIER_CLASS_ID.asSingleFqName(),
    )
    override fun StyleSheetBlock.Style.Variant.Definition.Expanded.createReplacementCode(): String {
        // Transform: `CssStyle.addVariant(...) { base { ... } }` to `CssStyle.addVariantBase(...) { ... }`
        val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
        val bodyStr = baseBodyText ?: MODIFIER_CLASS_ID.shortClassName.asString()

        return """
            $rootName.${ADD_VARIANT_BASE_CALLABLE_ID.callableName.asString()}$argStr {
                $bodyStr
            }
            """.trimIndent()
    }
}