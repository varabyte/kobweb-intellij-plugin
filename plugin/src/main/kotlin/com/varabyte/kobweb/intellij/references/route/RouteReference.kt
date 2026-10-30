package com.varabyte.kobweb.intellij.references.route

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.varabyte.kobweb.intellij.util.kobweb.route.LinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.allowsPages
import com.varabyte.kobweb.intellij.util.kobweb.route.findLedToBy
import com.varabyte.kobweb.intellij.util.kobweb.route.findPages
import com.varabyte.kobweb.intellij.util.kobweb.route.findPublicResource
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteRedirects
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoutePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import com.varabyte.kobweb.intellij.util.kobweb.route.mayHavePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.parseLinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.redirectedBy
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * A reference from a string that holds a route, e.g. the `"/blog/post"` in `Link("/blog/post")`, to the page it leads
 * to, after any redirects in the site's `.kobweb/conf.yaml` (see [findLedToBy]).
 *
 * Routes that lead to public files (e.g. `"/favicon.ico"`) get a [PublicFileReference] per segment instead.
 *
 * Segments that aren't known until runtime (e.g. the `$id` in `"/posts/$id"`) only match dynamic segments (see
 * [LinkPath.matches]).
 *
 * This reference is created for any absolute path used in a way that could hold a route (see [mayHavePathTarget]),
 * but it only resolves if it actually does (see [findRoutePathTarget]), which requires Kotlin analysis to check.
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
        val path = element.parseLinkPath() ?: return null
        val target = analyze(element) { element.findRoutePathTarget() } ?: return null

        val redirects = element.findRouteRedirects()
        // Where pages aren't allowed (e.g. an image's source), the browser loads the path from the server, which serves
        // public files without ever routing to a page. So a path that leads to a file doesn't lead to a page, even one
        // whose route it matches (e.g. "/images/cat.png" and a page at "/images/{...rest}").
        if (!target.allowsPages && element.findPublicResource(path.redirectedBy(redirects)) != null) return null

        return element.findPages().findLedToBy(path, redirects)?.function
    }

    // Renaming a page function doesn't change its route -- it is derived from the file name! So leave the string alone
    override fun handleElementRename(newElementName: String): PsiElement = element

    override fun bindToElement(element: PsiElement): PsiElement = this.element
}
