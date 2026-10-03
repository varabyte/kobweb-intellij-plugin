package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.style.EXTENDED_BY_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_CONCISE_EXTENDED_STYLE_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

class ConvertExtendedStyleToRelaxedFormatIntention : FormatStyleSheetSingletonBaseIntention(DERIVED_CONCISE_EXTENDED_STYLE_KEY) {
    override fun getText() = "Convert to relaxed CssStyle format"

    // It might seem opposite, but we accept *concise* blocks (so we can convert them to relaxed)
    override fun acceptCssBlock(cssStyleBlock: StyleSheetBlock.Style) =
        cssStyleBlock.type == StyleSheetBlock.Style.Type.EXTENDED && cssStyleBlock is StyleSheetBlock.Style.Concise

    override val imports: List<FqName> = listOf(EXTENDED_BY_CALLABLE_ID.asSingleFqName())
    override fun StyleSheetBlock.Style.createReplacementCode(): String {
        // Transform: `CssStyle.base(...) { ... }` to `CssStyle(...) { base { ... } }`
        val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
        val bodyStr = bodyText.orEmpty()

        return """
            $rootName.${EXTENDED_BY_CALLABLE_ID.callableName.asString()}$argStr {
                base {
                    $bodyStr
                }
            }
            """.trimIndent()
    }
}