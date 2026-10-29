package com.varabyte.kobweb.intellij.references.route

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.varabyte.kobweb.intellij.util.kobweb.route.LinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.findLedToBy
import com.varabyte.kobweb.intellij.util.kobweb.route.findPages
import com.varabyte.kobweb.intellij.util.kobweb.route.findPublicFolders
import com.varabyte.kobweb.intellij.util.kobweb.route.findPublicResource
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteRedirects
import com.varabyte.kobweb.intellij.util.kobweb.route.hasRoutePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.parseLinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.redirectedBy
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * A reference from one segment of a route string to the public file or folder it leads to (see [findPublicResource]),
 * e.g. from the `"images"` in `"/images/logo.png"` to the folder `public/images`, and from the `"logo.png"` to the file
 * in it.
 *
 * Each segment gets its own reference so that renaming or moving any file or folder along the path updates the string.
 *
 * These only resolve if the whole path leads to a public file and not to a page (which [RouteReference] leads to
 * instead), so that "/docs/intro" doesn't lead to a `public/docs` folder that just happens to exist. They also don't
 * resolve if one of the site's redirects applies to the path, as Kobweb sends it elsewhere before looking for files.
 *
 * @param segmentIndex The index of this reference's segment in the string's path (see [LinkPath.segments]).
 */
class PublicFileReference(
    element: KtStringTemplateExpression,
    private val segmentIndex: Int,
    range: TextRange,
) : PsiReferenceBase<KtStringTemplateExpression>(
    element,
    range,
    // Soft, as paths that don't lead to any file aren't necessarily errors (see RouteReference)
    /* soft = */ true,
) {
    override fun resolve(): PsiElement? {
        return ResolveCache.getInstance(element.project).resolveWithCaching(
            this,
            { reference, _ -> reference.doResolve() },
            /* needToPreventRecursion = */ false,
            /* incompleteCode = */ false,
        )
    }

    private fun doResolve(): PsiElement? {
        val path = element.parseLinkPath() ?: return null
        // Checked first, as it's the cheapest check and rules out most strings
        if (element.findPublicResource(path) == null) return null
        if (!analyze(element) { element.hasRoutePathTarget() }) return null
        val redirects = element.findRouteRedirects()
        // Kobweb applies redirects before serving public files, so a redirected path doesn't lead to its file
        if (path.redirectedBy(redirects) !== path) return null
        if (element.findPages().findLedToBy(path, redirects) != null) return null

        val file = element.findPublicResource(path.segments.take(segmentIndex + 1)) ?: return null
        val psiManager = PsiManager.getInstance(element.project)
        return if (file.isDirectory) psiManager.findDirectory(file) else psiManager.findFile(file)
    }

    override fun handleElementRename(newElementName: String): PsiElement =
        ElementManipulators.handleContentChange(element, rangeInElement, newElementName)

    override fun bindToElement(element: PsiElement): PsiElement {
        val file = (element as? PsiFileSystemItem)?.virtualFile ?: return this.element
        // If it was moved out of the public folders, Kobweb no longer serves it, so there's no path to update to
        val relativePath = this.element.findPublicFolders()
            .firstNotNullOfOrNull { folder -> VfsUtilCore.getRelativePath(file, folder) }
            ?: return this.element

        // Replace everything from the start of the path to the end of this segment, e.g. "images/logo.png" for the
        // last segment of "/images/logo.png", which leaves the slash before it and anything after it alone
        val path = this.element.parseLinkPath() ?: return this.element
        val range = TextRange(path.segments.first().sourceRange.first, rangeInElement.endOffset)
        return ElementManipulators.handleContentChange(this.element, range, relativePath)
    }
}
