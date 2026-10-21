package com.varabyte.kobweb.intellij.intentions.style.format

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.util.idea.intentions.CacheDerivedPsiElementIntentionAction
import com.varabyte.kobweb.intellij.util.idea.key
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import com.varabyte.kobweb.intellij.util.psi.addImportIfNeeded
import com.varabyte.kobweb.intellij.util.psi.removeImportIfUnused
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
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

    /**
     * How a replacement changes the file's imports.
     *
     * @property added Imports the replacement code needs, which will be added if they aren't already in scope.
     * @property removed Imports the original code may have needed but the replacement doesn't (e.g. for a function it
     *   swaps for another one), which will be removed if nothing else in the file uses them.
     */
    protected class ImportChanges(val added: List<FqName>, val removed: List<FqName>) {
        companion object {
            val None = ImportChanges(emptyList(), emptyList())
        }
    }

    protected class ImportChangesBuilder {
        private val added = mutableListOf<FqName>()
        private val removed = mutableListOf<FqName>()

        fun add(id: ClassId) { added += id.asSingleFqName() }
        fun add(id: CallableId) { added += id.asSingleFqName() }
        fun remove(id: CallableId) { removed += id.asSingleFqName() }

        /** Replace one import with another, e.g. when the replacement code swaps one function for another. */
        fun replace(from: CallableId, to: CallableId) {
            remove(from)
            add(to)
        }

        fun build() = ImportChanges(added.toList(), removed.toList())
    }

    protected fun buildImportChanges(block: ImportChangesBuilder.() -> Unit): ImportChanges {
        return ImportChangesBuilder().apply(block).build()
    }

    protected data class ReplacementCode(
        val imports: ImportChanges,
        @Language("kotlin") val code: String,
        val extractedComments: List<PsiComment> = emptyList(),
    )

    protected abstract fun StyleSheetBlock.Style.createReplacementCode(): ReplacementCode

    final override fun handleElementIsInvoked(project: Project, editor: Editor, element: KtNameReferenceExpression) {
        val cssStyleBlock = element.getStyleBlock() ?: return
        val containingFile = element.containingFile as? KtFile ?: return
        val factory = KtPsiFactory(project)

        with(cssStyleBlock.createReplacementCode()) {
            imports.added.forEach { import -> containingFile.addImportIfNeeded(import) }
            val commentTexts = extractedComments.map { it.text }
            val newExpr = factory.createExpression(code.trim())
            val replacedCode = cssStyleBlock.rootExpression.replace(newExpr)
            imports.removed.forEach { import -> containingFile.removeImportIfUnused(import) }

            // A style should always be assigned to a property, so if we have any comments we've extracted as part of
            // the intention action, let's add them above the property.
            if (commentTexts.isNotEmpty()) {
                val parentProperty = PsiTreeUtil.getParentOfType(replacedCode, KtProperty::class.java)
                if (parentProperty != null) {
                    val anchor = parentProperty.firstChild
                    commentTexts.forEach { text ->
                        parentProperty.addBefore(factory.createComment(text), anchor)
                        parentProperty.addBefore(factory.createNewLine(), anchor)
                    }
                }
            }
        }
    }
}