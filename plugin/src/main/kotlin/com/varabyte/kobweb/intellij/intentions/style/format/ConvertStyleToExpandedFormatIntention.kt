package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.style.ADD_VARIANT_BASE_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.ADD_VARIANT_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.CSS_STYLE_BASE_EXTENSION_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.EXTENDED_BY_BASE_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.EXTENDED_BY_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_CONCISE_FORMAT_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

class ConvertStyleToExpandedFormatIntention : FormatStyleBaseIntention(
    DERIVED_CONCISE_FORMAT_KEY,
    UxGlobals.textBundle.message("intentions.convert_style_to_expanded_format.name"),
    castStyleBlock = {
        when (it) {
            is StyleSheetBlock.Style.Definition.Concise,
            is StyleSheetBlock.Style.Extended.Concise,
            is StyleSheetBlock.Style.Variant.Definition.Concise,
            is StyleSheetBlock.Style.Variant.Extended.Concise -> it
            else -> null
        }
    }
) {
    // Transform: `CssStyle.base(...) { ... }` to `CssStyle(...) { base { ... } }`
    private fun StyleSheetBlock.Style.Definition.Concise.createReplacementCode() = ReplacementCode(
        buildImportChanges {
            remove(CSS_STYLE_BASE_EXTENSION_CALLABLE_ID)
        },
        run {
            // It's pretty rare, but you can have type arguments in your base method, as in CssStyle.base<T>, at which point
            // you need to move them to CssStyle<T> after converting.
            val typeArgs = baseCall.typeArgumentList?.text.orEmpty()
            val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
            val bodyStr = baseBodyText.orEmpty()

            """
            $rootName$typeArgs$argStr {
                base {
                    $bodyStr
                }
            }
            """.trimIndent()
        }
    )

    // Transform: `CssStyle.base(...) { ... }` to `CssStyle(...) { base { ... } }`
    private fun StyleSheetBlock.Style.Variant.Definition.Concise.createReplacementCode() = ReplacementCode(
        buildImportChanges {
            replace(ADD_VARIANT_BASE_CALLABLE_ID, ADD_VARIANT_CALLABLE_ID)
        },
        run {
            val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
            val bodyStr = baseBodyText.orEmpty()

            """
            $rootName.${ADD_VARIANT_CALLABLE_ID.callableName.asString()}$argStr {
                base {
                    $bodyStr
                }
            }
            """.trimIndent()
        }
    )

    private fun StyleSheetBlock.Style.Extended.Concise.createReplacementCode() = ReplacementCode(
        buildImportChanges {
            replace(EXTENDED_BY_BASE_CALLABLE_ID, EXTENDED_BY_CALLABLE_ID)
        },
        run {
            // Transform: `SomeStyle.extendedByBase(...) { ... }` to `SomeStyle.extendedBy(...) { base { ... } }`
            val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
            val bodyStr = baseBodyText.orEmpty()

            """
            $rootName.${EXTENDED_BY_CALLABLE_ID.callableName.asString()}$argStr {
                base {
                    $bodyStr
                }
            }
            """.trimIndent()
        }
    )

    // Transform: `SomeVariant.extendedByBase(...) { ... }` to `SomeVariant.extendedBy(...) { base { ... } }`
    private fun StyleSheetBlock.Style.Variant.Extended.Concise.createReplacementCode() = ReplacementCode(
        buildImportChanges {
            replace(EXTENDED_BY_BASE_CALLABLE_ID, EXTENDED_BY_CALLABLE_ID)
        },
        run {
            val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
            val bodyStr = baseBodyText.orEmpty()

            """
            $rootName.${EXTENDED_BY_CALLABLE_ID.callableName.asString()}$argStr {
                base {
                    $bodyStr
                }
            }
            """.trimIndent()
        }
    )

    override fun StyleSheetBlock.Style.deriveIntentionText(): String? {
        return when (this) {
            is StyleSheetBlock.Style.Definition.Concise -> UxGlobals.textBundle.message("intentions.convert_style_to_expanded_format.text")
            is StyleSheetBlock.Style.Extended.Concise -> UxGlobals.textBundle.message("intentions.convert_extended_style_to_expanded_format.text")
            is StyleSheetBlock.Style.Variant.Definition.Concise -> UxGlobals.textBundle.message("intentions.convert_style_variant_to_expanded_format.text")
            is StyleSheetBlock.Style.Variant.Extended.Concise -> UxGlobals.textBundle.message("intentions.convert_extended_style_variant_to_expanded_format.text")
            else -> null
        }
    }

    override fun StyleSheetBlock.Style.createReplacementCode(): ReplacementCode {
        return when (this) {
            is StyleSheetBlock.Style.Definition.Concise -> createReplacementCode()
            is StyleSheetBlock.Style.Extended.Concise -> createReplacementCode()
            is StyleSheetBlock.Style.Variant.Definition.Concise -> createReplacementCode()
            is StyleSheetBlock.Style.Variant.Extended.Concise -> createReplacementCode()
            else -> error("Unexpected style type: ${this::class.simpleName}")
        }
    }
}