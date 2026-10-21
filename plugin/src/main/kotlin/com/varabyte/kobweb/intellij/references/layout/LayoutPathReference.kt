package com.varabyte.kobweb.intellij.references.layout

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.varabyte.kobweb.intellij.util.kobweb.PackageShortcuts
import com.varabyte.kobweb.intellij.util.kobweb.layout.LayoutDefinition
import com.varabyte.kobweb.intellij.util.kobweb.layout.findLayout
import com.varabyte.kobweb.intellij.util.kobweb.layout.findLayouts
import com.varabyte.kobweb.intellij.util.kobweb.layout.kobwebGroup
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Returns the range of the layout's name inside a layout path string, i.e. the part of the path after its last period.
 */
private fun KtStringTemplateExpression.layoutNameRange(): TextRange {
    val pathRange = ElementManipulators.getValueTextRange(this)
    val path = ElementManipulators.getValueText(this)
    return TextRange(pathRange.startOffset + path.lastIndexOf('.') + 1, pathRange.endOffset)
}

/**
 * Returns every dot-separated suffix of this path, e.g. ".a.b.C" -> [".a.b.C", "a.b.C", "b.C", "C"].
 */
private fun String.dottedSuffixes(): List<String> {
    return listOf(this) + indices.filter { this[it] == '.' }.map { substring(it + 1) }
}

/**
 * A reference from the path inside a `@Layout("...")` annotation to the layout function it points at.
 *
 * For example, `@Layout(".components.layouts.PageLayout")` points at `fun PageLayout` in the
 * `com.example.components.layouts` package (assuming the project's group is "com.example").
 *
 * Note that the reference itself only covers the layout's name (e.g. `PageLayout`), as that's the only part a rename
 * changes. (Notably, renaming in place edits exactly the range of each reference.) Use [path] for the whole path.
 */
class LayoutPathReference(element: KtStringTemplateExpression) : PsiReferenceBase<KtStringTemplateExpression>(
    element,
    element.layoutNameRange(),
    // Soft, as we handle reporting invalid paths ourselves
    /* soft = */ true,
) {
    /**
     * The whole path, e.g. ".components.layouts.PageLayout".
     */
    val path: String get() = ElementManipulators.getValueText(element)

    /**
     * The range of the whole path within [element] (i.e. without its quotes).
     *
     * This is in contrast to [rangeInElement], which only covers the layout's name.
     */
    val pathRange: TextRange get() = ElementManipulators.getValueTextRange(element)

    /**
     * Returns the layout this path points at, or null if it doesn't point at one.
     *
     * Prefer this over [resolve] to check whether a path is valid, as a library can advertise a layout that we can't
     * find a declaration for (in which case this returns a layout but [resolve] returns null).
     */
    fun resolveLayout(): LayoutDefinition? {
        val fqn = PackageShortcuts.resolve(element.kobwebGroup, path) ?: return null
        return element.findLayout(fqn.asString())
    }

    override fun resolve(): PsiElement? = resolveLayout()?.function

    override fun getVariants(): Array<Any> {
        // During completion, `element` lives in a temporary copy of the file (with a dummy identifier inserted at the
        // caret). Map it back to the real file, so we can compare it against PSI from the project (e.g. to exclude the
        // layout we're inside) and look up which Kobweb project it belongs to.
        val originalElement = CompletionUtil.getOriginalOrSelf(element)
        val group = originalElement.kobwebGroup
        // A layout can't be its own parent
        val annotatedFunction = originalElement.getStrictParentOfType<KtNamedFunction>()

        return originalElement.findLayouts()
            .filter { layout -> layout.function == null || layout.function != annotatedFunction }
            .map { layout ->
                val path = PackageShortcuts.relativize(group, FqName(layout.fqn))
                LookupElementBuilder.create(layout.function ?: layout.fqn, path)
                    // Completion only matches against what's typed after the last period (since that's where this
                    // reference starts), so match each part of the path, e.g. typing ".components.lay" matches
                    // "layouts.PageLayout", and typing "PageLa" matches "PageLayout"
                    .withLookupStrings(path.dottedSuffixes() + layout.fqn.dottedSuffixes())
                    .withPresentableText(layout.shortName)
                    .withTailText(" (${layout.fqn.substringBeforeLast('.', "<root>")})", true)
                    .withIcon(KotlinIcons.FUNCTION)
                    .withInsertHandler(ReplaceWholePathInsertHandler)
            }
            .toTypedArray()
    }
}

/**
 * Makes a completed path replace the whole string, rather than just the part after its last period.
 *
 * Completion only replaces the reference's range, which is just the layout's name, so anything typed before it (e.g.
 * the ".components." in ".components.PageLa") would otherwise stay in front of the inserted path.
 */
private object ReplaceWholePathInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        PsiDocumentManager.getInstance(context.project).commitDocument(context.document)
        val stringExpr = context.file.findElementAt(context.startOffset)
            ?.getStrictParentOfType<KtStringTemplateExpression>()
            ?: return
        val pathStart = stringExpr.textRange.startOffset + ElementManipulators.getValueTextRange(stringExpr).startOffset
        if (pathStart < context.startOffset) context.document.deleteString(pathStart, context.startOffset)
    }
}
