package com.varabyte.kobweb.intellij.intentions.style.format

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.idea.key
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument

private val CSS_STYLE_BLOCK_KEY by key<StyleSheetBlock.Style>()

internal fun StyleSheetBlock.Style.blockIsEmptyOrOnlyContainsBaseBlock(): Boolean {
    return styleBlock.statements.none { it != this.baseCall }
}

abstract class FormatStyleBaseIntention(
    cacheKey: Key<Pair<PsiElement, KtNameReferenceExpression>>,
    familyName: String,
    private val castStyleBlock: (StyleSheetBlock.Style) -> StyleSheetBlock.Style?)
    : CacheDerivedPsiElementIntentionAction<KtNameReferenceExpression>(cacheKey, familyName) {

    protected fun PsiElement.getStyleBlock(): StyleSheetBlock.Style? {
        return getUserData(CSS_STYLE_BLOCK_KEY)
    }

    open fun StyleSheetBlock.Style.deriveIntentionText(): String? = null

    final override fun PsiElement.tryDerivingElement(): KtNameReferenceExpression? {
        val element = this as? KtNameReferenceExpression
            // Check in case cursor is at the end of the name
            ?: this.prevSibling as? KtNameReferenceExpression
            ?: this.parent as? KtNameReferenceExpression
            ?: return null

        val cssStyleBlock = analyze(element) {
            StyleSheetBlock.Style.containing(element)?.let { castStyleBlock(it) } ?: return null
        }
        element.putUserData(CSS_STYLE_BLOCK_KEY, cssStyleBlock)
        return element
    }

    override fun handleElementIsAvailable(
        project: Project,
        editor: Editor,
        element: KtNameReferenceExpression
    ) {
        text = element.getStyleBlock()?.deriveIntentionText() ?: familyName
    }

    protected fun KtValueArgument.wrapInParentheses() = "($text)"

    protected class ReplacementCode(
        val imports: List<FqName>,
        @Language("kotlin") val code: String
    ) {
        constructor(@Language("kotlin") code: String) : this(emptyList(), code)
    }
    protected abstract fun StyleSheetBlock.Style.createReplacementCode(): ReplacementCode

    final override fun handleElementIsInvoked(project: Project, editor: Editor, element: KtNameReferenceExpression) {
        val cssStyleBlock = element.getStyleBlock() ?: return
        val containingFile = element.containingFile as? KtFile ?: return
        val factory = KtPsiFactory(project)

        with(cssStyleBlock.createReplacementCode()) {
            if (imports.isNotEmpty()) {
                imports.forEach { import -> containingFile.addImport(import) }
            }
            val newExpr = factory.createExpression(code.trim())
            cssStyleBlock.rootExpression.replace(newExpr)
        }
    }
}