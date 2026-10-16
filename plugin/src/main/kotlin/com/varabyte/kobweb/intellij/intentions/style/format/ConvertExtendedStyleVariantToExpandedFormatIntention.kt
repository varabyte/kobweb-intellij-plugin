package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.style.EXTENDED_BY_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_CONCISE_EXTENDED_STYLE_VARIANT_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

// It might seem opposite, but we accept *concise* blocks (so we can convert them to expanded)
class ConvertExtendedStyleVariantToExpandedFormatIntention : FormatStyleSheetSingletonBaseIntention<StyleSheetBlock.Style.Variant.Extended.Concise>(
    DERIVED_CONCISE_EXTENDED_STYLE_VARIANT_KEY,
    UxGlobals.textBundle.message("intentions.convert_extended_style_variant_to_exapnded_format.name"),
    castStyleBlock = { (it as? StyleSheetBlock.Style.Variant.Extended.Concise) }
) {
    override val imports: List<FqName> = listOf(EXTENDED_BY_CALLABLE_ID.asSingleFqName())
    override fun StyleSheetBlock.Style.Variant.Extended.Concise.createReplacementCode(): String {
        // Transform: `SomeVariant.extendedByBase(...) { ... }` to `SomeVariant.extendedBy(...) { base { ... } }`
        val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
        val bodyStr = baseBodyText.orEmpty()

        return """
            $rootName.${EXTENDED_BY_CALLABLE_ID.callableName.asString()}$argStr {
                base {
                    $bodyStr
                }
            }
            """.trimIndent()
    }
}