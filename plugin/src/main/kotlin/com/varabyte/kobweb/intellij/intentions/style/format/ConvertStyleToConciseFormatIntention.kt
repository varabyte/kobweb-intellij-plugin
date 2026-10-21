package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.modifier.MODIFIER_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.ADD_VARIANT_BASE_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.ADD_VARIANT_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.CSS_STYLE_BASE_EXTENSION_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.EXTENDED_BY_BASE_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.EXTENDED_BY_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import com.varabyte.kobweb.intellij.util.psi.findAttachedComments
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_EXPANDED_FORMAT_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

class ConvertStyleToConciseFormatIntention : FormatStyleBaseIntention(
    DERIVED_EXPANDED_FORMAT_KEY,
    UxGlobals.textBundle.message("intentions.convert_style_to_concise_format.name"),
    castStyleBlock = {
        when (it) {
            is StyleSheetBlock.Style.Definition.Expanded,
            is StyleSheetBlock.Style.Extended.Expanded,
            is StyleSheetBlock.Style.Variant.Definition.Expanded,
            is StyleSheetBlock.Style.Variant.Extended.Expanded -> it
            else -> null
        }
            // Do not allow compressing a block that has pseudo-selectors already declared, e.g. `hover`, `focus`
            ?.takeIf { style -> style.blockIsEmptyOrOnlyContainsBaseBlock() }
    }
) {
    // Transform: `CssStyle(...) { base { ... } }` to `CssStyle.base(...) { ... }`
    private fun StyleSheetBlock.Style.Definition.Expanded.createReplacementCode() = ReplacementCode(
        buildImportChanges {
            add(MODIFIER_CLASS_ID)
            add(CSS_STYLE_BASE_EXTENSION_CALLABLE_ID)
        },
        run {
            // It's pretty rare, but you can have type arguments in your CssStyle method, as in CssStyle<T>, at which point
            // you need to move them to CssStyle.base<T> after converting.
            val typeArgs = rootExpression.typeArgumentList?.text.orEmpty()
            val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
            val bodyStr = baseBodyText ?: MODIFIER_CLASS_ID.shortClassName.asString()

            """
            $rootName.${CSS_STYLE_BASE_EXTENSION_CALLABLE_ID.callableName.asString()}$typeArgs$argStr {
                $bodyStr
            }
            """.trimIndent()
        }
    )

    // Transform: `SomeStyle.extendedBy(...) { base { ... } }` to `SomeStyle.extendedByBase(...) { ... }`
    private fun StyleSheetBlock.Style.Extended.Expanded.createReplacementCode() = ReplacementCode(
        buildImportChanges {
            add(MODIFIER_CLASS_ID)
            replace(EXTENDED_BY_CALLABLE_ID, EXTENDED_BY_BASE_CALLABLE_ID)
        },
        run {
            val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
            val bodyStr = baseBodyText ?: MODIFIER_CLASS_ID.shortClassName.asString()

            """
                $rootName.${EXTENDED_BY_BASE_CALLABLE_ID.callableName.asString()}$argStr {
                    $bodyStr
                }
                """.trimIndent()
        }
    )

    // Transform: `CssStyle.addVariant(...) { base { ... } }` to `CssStyle.addVariantBase(...) { ... }`
    private fun StyleSheetBlock.Style.Variant.Definition.Expanded.createReplacementCode() = ReplacementCode(
        buildImportChanges {
            add(MODIFIER_CLASS_ID)
            replace(ADD_VARIANT_CALLABLE_ID, ADD_VARIANT_BASE_CALLABLE_ID)
        },
        run {
            val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
            val bodyStr = baseBodyText ?: MODIFIER_CLASS_ID.shortClassName.asString()

            """
            $rootName.${ADD_VARIANT_BASE_CALLABLE_ID.callableName.asString()}$argStr {
                $bodyStr
            }
            """.trimIndent()
        }
    )

    // Transform: `SomeVariant.extendedBy(...) { base { ... } }` to `SomeVariant.extendedByBase(...) { ... }`
    private fun StyleSheetBlock.Style.Variant.Extended.Expanded.createReplacementCode() = ReplacementCode(
        buildImportChanges {
            add(MODIFIER_CLASS_ID)
            replace(EXTENDED_BY_CALLABLE_ID, EXTENDED_BY_BASE_CALLABLE_ID)
        },
        run {
            val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
            val bodyStr = baseBodyText ?: MODIFIER_CLASS_ID.shortClassName.asString()

            """
            $rootName.${EXTENDED_BY_BASE_CALLABLE_ID.callableName.asString()}$argStr {
                $bodyStr
            }
            """.trimIndent()
        }
    )

    override fun StyleSheetBlock.Style.deriveIntentionText(): String? {
        return when (this) {
            is StyleSheetBlock.Style.Definition.Expanded -> UxGlobals.textBundle.message("intentions.convert_style_to_concise_format.text")
            is StyleSheetBlock.Style.Extended.Expanded -> UxGlobals.textBundle.message("intentions.convert_extended_style_to_concise_format.text")
            is StyleSheetBlock.Style.Variant.Definition.Expanded -> UxGlobals.textBundle.message("intentions.convert_style_variant_to_concise_format.text")
            is StyleSheetBlock.Style.Variant.Extended.Expanded -> UxGlobals.textBundle.message("intentions.convert_extended_style_variant_to_concise_format.text")
            else -> null
        }
    }

    override fun StyleSheetBlock.Style.createReplacementCode(): ReplacementCode {
        val extractedComments = baseCall?.findAttachedComments()?.overhead.orEmpty()

        return when (this) {
            is StyleSheetBlock.Style.Definition.Expanded -> createReplacementCode()
            is StyleSheetBlock.Style.Extended.Expanded -> createReplacementCode()
            is StyleSheetBlock.Style.Variant.Definition.Expanded -> createReplacementCode()
            is StyleSheetBlock.Style.Variant.Extended.Expanded -> createReplacementCode()
            else -> error("Unexpected style type: ${this::class.simpleName}")
        }.copy(extractedComments = extractedComments)
    }
}