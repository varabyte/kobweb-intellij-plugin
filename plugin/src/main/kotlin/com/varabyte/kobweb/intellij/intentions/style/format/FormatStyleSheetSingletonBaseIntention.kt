package com.varabyte.kobweb.intellij.intentions.style.format

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.idea.key
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument

private val CSS_STYLE_FORMAT_KEY by key<StyleSheetBlock.Style>()

internal fun StyleSheetBlock.Style.blockIsEmptyOrOnlyContainsBaseBlock(): Boolean {
    return styleBlock.statements.none { it != this.baseCall }
}

abstract class FormatStyleSheetSingletonBaseIntention<S: StyleSheetBlock.Style>(
    cacheKey: Key<Pair<PsiElement, KtNameReferenceExpression>>,
    private val castStyleBlock: (StyleSheetBlock.Style) -> S?)
    : CacheDerivedPsiElementIntentionAction<KtNameReferenceExpression>(cacheKey) {

    protected fun PsiElement.getStyleBlock(): S? {
        @Suppress("UNCHECKED_CAST")
        return getUserData(CSS_STYLE_FORMAT_KEY) as S?
    }

    final override fun PsiElement.tryDerivingElement(): KtNameReferenceExpression? {
        val element = this as? KtNameReferenceExpression
            // Check in case cursor is at the end of the name
            ?: this.prevSibling as? KtNameReferenceExpression
            ?: this.parent as? KtNameReferenceExpression
            ?: return null

        val cssStyleFormat = analyze(element) {
            StyleSheetBlock.Style.containing(element)?.let { castStyleBlock(it) } ?: return null
        }
        element.putUserData(CSS_STYLE_FORMAT_KEY, cssStyleFormat)
        return element
    }

    protected fun KtValueArgument.wrapInParentheses() = "($text)"

    protected open val imports: List<FqName> = emptyList()

    protected abstract fun S.createReplacementCode(): String

    final override fun handleElementIsInvoked(project: Project, editor: Editor, element: KtNameReferenceExpression) {
        val cssStyleBlock = element.getStyleBlock() ?: return
        val containingFile = element.containingFile as? KtFile ?: return
        val factory = KtPsiFactory(project)

        if (imports.isNotEmpty()) {
            imports.forEach { import -> containingFile.addImport(import) }
        }
        val newExpr = factory.createExpression(cssStyleBlock.createReplacementCode().trim())
        cssStyleBlock.rootExpression.replace(newExpr)
    }
}