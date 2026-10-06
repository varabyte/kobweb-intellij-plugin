package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.style.EXTENDED_BY_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_CONCISE_EXTENDED_STYLE_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

// It might seem opposite, but we accept *concise* blocks (so we can convert them to relaxed)
class ConvertExtendedStyleToRelaxedFormatIntention : FormatStyleSheetSingletonBaseIntention<StyleSheetBlock.Style.Extended.Concise>(
    DERIVED_CONCISE_EXTENDED_STYLE_KEY,
    castStyleBlock = { (it as? StyleSheetBlock.Style.Extended.Concise) }
) {
    override fun getText() = "Convert to relaxed CssStyle format"

    override val imports: List<FqName> = listOf(EXTENDED_BY_CALLABLE_ID.asSingleFqName())
    override fun StyleSheetBlock.Style.Extended.Concise.createReplacementCode(): String {
        // Transform: `SomeStyle.extendedByBase(...) { ... }` to `SomeStyle.extendedBy(...) { base { ... } }`
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