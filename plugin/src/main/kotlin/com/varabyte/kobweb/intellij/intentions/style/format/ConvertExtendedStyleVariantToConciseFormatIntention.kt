package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.modifier.MODIFIER_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.EXTENDED_BY_BASE_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_RELAXED_STYLE_VARIANT_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

// It might seem opposite, but we accept *relaxed* blocks (so we can convert them to concise)
class ConvertExtendedStyleVariantToConciseFormatIntention : FormatStyleSheetSingletonBaseIntention<StyleSheetBlock.Style.Variant.Extended.Relaxed>(
    DERIVED_RELAXED_STYLE_VARIANT_KEY,
    // Do not allow compressing a CssStyle variant block that has pseudo-selectors already declared, e.g. `hover`, `focus`
    castStyleBlock = { (it as? StyleSheetBlock.Style.Variant.Extended.Relaxed)?.takeIf { it.blockIsEmptyOrOnlyContainsBaseBlock() } }
) {
    override fun getText() = "Convert to concise CssStyleVariant format"

    override val imports: List<FqName> = listOf(
        EXTENDED_BY_BASE_CALLABLE_ID.asSingleFqName(),
        MODIFIER_CLASS_ID.asSingleFqName(),
    )
    override fun StyleSheetBlock.Style.Variant.Extended.Relaxed.createReplacementCode(): String {
        // Transform: `SomeVariant.extendedBy(...) { base { ... } }` to `SomeVariant.extendedByBase(...) { ... }`
        val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
        val bodyStr = baseBodyText ?: MODIFIER_CLASS_ID.shortClassName.asString()

        return """
            $rootName.${EXTENDED_BY_BASE_CALLABLE_ID.callableName.asString()}$argStr {
                $bodyStr
            }
            """.trimIndent()
    }
}