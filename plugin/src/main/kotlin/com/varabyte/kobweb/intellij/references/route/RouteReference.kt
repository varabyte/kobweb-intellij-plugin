package com.varabyte.kobweb.intellij.references.route

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.varabyte.kobweb.intellij.util.kobweb.route.LinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.findLedToBy
import com.varabyte.kobweb.intellij.util.kobweb.route.findPages
import com.varabyte.kobweb.intellij.util.kobweb.route.findPublicResource
import com.varabyte.kobweb.intellij.util.kobweb.route.hasRoutePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * A reference from a string that holds a route, e.g. the `"/blog/post"` in `Link("/blog/post")`, to the page it leads
 * to, or else to the public file it leads to (e.g. `"/favicon.ico"`, see [findPublicResource]).
 *
 * Segments that aren't known until runtime (e.g. the `$id` in `"/posts/$id"`) only match dynamic segments (see
 * [LinkPath.matches]).
 *
 * This reference is created for any absolute path passed into a function or compared against something, but it only
 * resolves if that holds a route (see [hasRoutePathTarget]), which requires Kotlin analysis to check.
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
        if (!analyze(element) { element.hasRoutePathTarget() }) return null

        element.findPages().findLedToBy(path)?.function?.let { return it }

        // Otherwise, it may lead to a file that Kobweb serves as is, e.g. "/favicon.ico"
        val publicFile = element.findPublicResource(path) ?: return null
        val psiManager = PsiManager.getInstance(element.project)
        return if (publicFile.isDirectory) psiManager.findDirectory(publicFile) else psiManager.findFile(publicFile)
    }

    // Renaming a page function doesn't change its route -- it is derived from the file name! So leave the string alone
    override fun handleElementRename(newElementName: String): PsiElement = element

    override fun bindToElement(element: PsiElement): PsiElement = this.element
}
