package com.varabyte.kobweb.intellij.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.idea.key
import com.varabyte.kobweb.intellij.util.kobweb.style.CssStyleBlock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

private val STYLE_KIND_KEY by key<CssStyleBlock>()

private val CONTEXT_KEYWORDS = setOf("base", "CssStyle")

abstract class CssStyleFormatBaseIntention(cacheKey: Key<Pair<PsiElement, KtNameReferenceExpression>>)
    : CacheDerivedPsiElementIntentionAction<KtNameReferenceExpression>(cacheKey) {

    abstract fun acceptCssBlock(cssStyleBlock: CssStyleBlock): Boolean
    protected fun PsiElement.getStyleBlock(): CssStyleBlock? { return getUserData(STYLE_KIND_KEY) }

    final override fun PsiElement.tryDerivingElement(): KtNameReferenceExpression? {
        val element = this as? KtNameReferenceExpression
            // Check in case cursor is at the end of the name
            ?: this.prevSibling as? KtNameReferenceExpression
            ?: this.parent as? KtNameReferenceExpression
            ?: return null
        if (element.getReferencedName() !in CONTEXT_KEYWORDS) return null

        val cssStyleFormat = analyze(element) {
            CssStyleBlock.detect(element)?.takeIf { acceptCssBlock(it) } ?: return null
        }
        element.putUserData(STYLE_KIND_KEY, cssStyleFormat)
        return element
    }

    protected abstract fun CssStyleBlock.createReplacementCode(): String

    final override fun handleElementIsInvoked(project: Project, editor: Editor, element: KtNameReferenceExpression) {
        val cssStyleBlock = element.getStyleBlock() ?: return
        val factory = KtPsiFactory(project)
        val newExpr = factory.createExpression(cssStyleBlock.createReplacementCode().trim())
        cssStyleBlock.rootExpression.replace(newExpr)
    }

}