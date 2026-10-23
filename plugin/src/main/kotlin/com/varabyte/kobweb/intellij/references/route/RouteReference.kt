package com.varabyte.kobweb.intellij.references.route

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.varabyte.kobweb.intellij.util.kobweb.route.LinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.findLedToBy
import com.varabyte.kobweb.intellij.util.kobweb.route.findPages
import com.varabyte.kobweb.intellij.util.kobweb.route.isRoutePathArgument
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * The parts of this string, in order, for parsing as a [LinkPath]: its text (with escape sequences decoded), or null for
 * template expressions (e.g. "$id"), whose values can't be known until runtime.
 */
private val KtStringTemplateExpression.linkPathParts: List<String?>
    get() = entries.map { entry ->
        when (entry) {
            is KtLiteralStringTemplateEntry -> entry.text
            is KtEscapeStringTemplateEntry -> entry.unescapedValue
            else -> null
        }
    }

/**
 * The path in this string, if it's one that could lead to a page (see [LinkPath.parse]).
 */
val KtStringTemplateExpression.linkPath: LinkPath? get() = LinkPath.parse(linkPathParts)

/**
 * Returns the range of the path inside this string, i.e. without any query parameters or fragment.
 */
private fun KtStringTemplateExpression.linkPathRange(): TextRange {
    val valueRange = ElementManipulators.getValueTextRange(this)
    val queryOrFragmentStart = entries
        .filterIsInstance<KtLiteralStringTemplateEntry>()
        .firstNotNullOfOrNull { entry ->
            entry.text.indexOfAny(charArrayOf('?', '#')).takeIf { it >= 0 }?.let { entry.startOffsetInParent + it }
        }
    return if (queryOrFragmentStart != null) TextRange(valueRange.startOffset, queryOrFragmentStart) else valueRange
}

/**
 * A reference from a string that holds a route, e.g. the `"/blog/post"` in `Link("/blog/post")`, to the page it leads
 * to.
 *
 * Segments that aren't known until runtime (e.g. the `$id` in `"/posts/$id"`) only match dynamic segments (see
 * [LinkPath.matches]).
 *
 * This reference is created for any absolute path passed into a function, but it only resolves if the parameter takes
 * a route (see [isRoutePathArgument]), which requires Kotlin analysis to check.
 *
 * Note that a page's route comes from its file and package, not its function's name, so renaming a page function
 * doesn't change any strings.
 */
class RouteReference(element: KtStringTemplateExpression) : PsiReferenceBase<KtStringTemplateExpression>(
    element,
    element.linkPathRange(),
    // Soft, as routes that don't lead to any page aren't necessarily errors (e.g. they might be served by the backend)
    /* soft = */ true,
) {
    override fun resolve(): PsiElement? {
        return ResolveCache.getInstance(element.project).resolveWithCaching(
            this,
            ResolveCache.AbstractResolver<RouteReference, PsiElement> { reference, _ -> reference.doResolve() },
            /* needToPreventRecursion = */ false,
            /* incompleteCode = */ false,
        )
    }

    private fun doResolve(): PsiElement? {
        val path = element.linkPath ?: return null
        if (!analyze(element) { element.isRoutePathArgument() }) return null

        return element.findPages().findLedToBy(path)?.function
    }

    // Renaming a page function doesn't change its route -- it is derived from the file name! So leave the string alone
    override fun handleElementRename(newElementName: String): PsiElement = element

    override fun bindToElement(element: PsiElement): PsiElement = this.element
}
