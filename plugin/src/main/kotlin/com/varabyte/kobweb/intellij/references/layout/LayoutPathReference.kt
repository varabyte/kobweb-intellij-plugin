package com.varabyte.kobweb.intellij.references.layout

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.ElementManipulators
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
 * A reference from the path inside a `@Layout("...")` annotation to the layout function it points at.
 *
 * For example, `@Layout(".components.layouts.PageLayout")` points at `fun PageLayout` in the
 * `com.example.components.layouts` package (assuming the project's group is "com.example").
 */
class LayoutPathReference(element: KtStringTemplateExpression) : PsiReferenceBase<KtStringTemplateExpression>(
    element,
    ElementManipulators.getValueTextRange(element),
    // Soft, as we handle reporting invalid paths ourselves
    /* soft = */ true,
) {
    /**
     * Returns the layout this path points at, or null if it doesn't point at one.
     *
     * Prefer this over [resolve] to check whether a path is valid, as a library can advertise a layout that we can't
     * find a declaration for (in which case this returns a layout but [resolve] returns null).
     */
    fun resolveLayout(): LayoutDefinition? {
        val fqn = PackageShortcuts.resolve(element.kobwebGroup, value) ?: return null
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
                LookupElementBuilder.create(layout.function ?: layout.fqn, PackageShortcuts.relativize(group, FqName(layout.fqn)))
                    // Allow users to search for the layout by its name, e.g. typing "PageLa" finds "PageLayout"
                    .withLookupStrings(listOf(layout.shortName, layout.fqn))
                    .withPresentableText(layout.shortName)
                    .withTailText(" (${layout.fqn.substringBeforeLast('.', "<root>")})", true)
                    .withIcon(KotlinIcons.FUNCTION)
            }
            .toTypedArray()
    }
}
