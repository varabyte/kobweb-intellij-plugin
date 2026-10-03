package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.style.CSS_STYLE_BASE_EXTENSION_CALLABLE_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_RELAXED_STYLE_DEFINITION_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

class ConvertStyleDefinitionToConciseFormatIntention : FormatStyleSheetSingletonBaseIntention(DERIVED_RELAXED_STYLE_DEFINITION_KEY) {
    override fun getText() = "Convert to concise CssStyle format"

    // It might seem opposite, but we accept *relaxed* blocks (so we can convert them to concise)
    override fun acceptCssBlock(cssStyleBlock: StyleSheetBlock.Style) =
        // Do not allow compressing a CssStyle block that has pseudo-selectors already declared, e.g. `hover`, `focus`
        cssStyleBlock.type == StyleSheetBlock.Style.Type.DEFINITION && cssStyleBlock is StyleSheetBlock.Style.Relaxed && cssStyleBlock.otherCalls.isEmpty()


    override val imports: List<FqName> = listOf(CSS_STYLE_BASE_EXTENSION_CALLABLE_ID.asSingleFqName())
    override fun StyleSheetBlock.Style.createReplacementCode(): String {
        // Transform: `CssStyle(...) { base { ... } }` to `CssStyle.base(...) { ... }`

        // It's pretty rare, but you can have type arguments in your CssStyle method, as in CssStyle<T>, at which point
        // you need to move them to CssStyle.base<T> after converting.
        val typeArgs = (rootExpression as KtCallExpression).typeArgumentList?.text.orEmpty()
        val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
        val bodyStr = bodyText.orEmpty()

        return """
            $rootName.${CSS_STYLE_BASE_EXTENSION_CALLABLE_ID.callableName.asString()}$typeArgs$argStr {
                $bodyStr
            }
            """.trimIndent()
    }
}