package com.varabyte.kobweb.intellij.references.route

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.varabyte.kobweb.intellij.util.kobweb.route.LinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.findLedToBy
import com.varabyte.kobweb.intellij.util.kobweb.route.findPages
import com.varabyte.kobweb.intellij.util.kobweb.route.isRoutePathArgument
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

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
