package com.varabyte.kobweb.intellij.intentions.style.format

import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

private val DERIVED_CONCISE_STYLE_DEFINITION_KEY by CacheDerivedPsiElementIntentionAction.key<KtNameReferenceExpression>()

// It might seem opposite, but we accept *concise* blocks (so we can convert them to relaxed)
class ConvertStyleDefinitionToRelaxedFormatIntention : FormatStyleSheetSingletonBaseIntention<StyleSheetBlock.Style.Definition.Concise>(
    DERIVED_CONCISE_STYLE_DEFINITION_KEY,
    castStyleBlock = { (it as? StyleSheetBlock.Style.Definition.Concise) }
) {

    override fun getText() = "Convert to relaxed CssStyle format"

    override fun StyleSheetBlock.Style.Definition.Concise.createReplacementCode(): String {
        // Transform: `CssStyle.base(...) { ... }` to `CssStyle(...) { base { ... } }`

        // It's pretty rare, but you can have type arguments in your base method, as in CssStyle.base<T>, at which point
        // you need to move them to CssStyle<T> after converting.
        val typeArgs = baseCall.typeArgumentList?.text.orEmpty()
        val argStr = extraModifierArg?.wrapInParentheses().orEmpty()
        val bodyStr = bodyText.orEmpty()

        return """
            $rootName$typeArgs$argStr {
                base {
                    $bodyStr
                }
            }
            """.trimIndent()
    }
}