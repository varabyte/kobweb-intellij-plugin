package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.model.KobwebModel
import com.varabyte.kobweb.intellij.util.kobweb.KOBWEB_METADATA_MODULE
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.kotlin.config.ResourceKotlinRootType
import org.jetbrains.kotlin.idea.base.util.module

/**
 * Returns the folders whose files Kobweb serves from, e.g. `src/jsMain/resources/public`.
 *
 * This includes the public folders in the resources of this element's module plus any Kobweb modules it depends on,
 * followed by those of any Kobweb libraries it depends on (whose public files Kobweb copies into the site).
 *
 * If a file called `resume.pdf` was found in any of the public folders, it would get served at "/resume.pdf".
 */
fun PsiElement.findPublicFolders(): List<VirtualFile> {
    val module = module ?: return emptyList()
    fun VirtualFile.findFolder(relativePath: String) =
        findFileByRelativePath(relativePath.trim('/'))?.takeIf { it.isDirectory }

    val modules = buildList {
        OrderEnumerator.orderEntries(module).recursively().withoutSdk().withoutLibraries().productionOnly()
            .forEachModule { dependency -> add(dependency); true }
    }
    val moduleFolders = modules.flatMap { dependency ->
        // Kobweb only serves public files from Kobweb projects
        val kobwebProject = dependency.findKobwebProject() ?: return@flatMap emptyList()
        // Kotlin multiplatform modules mark their resources with Kotlin's own root type
        ModuleRootManager.getInstance(dependency)
            .getSourceRoots(setOf(JavaResourceRootType.RESOURCE, ResourceKotlinRootType))
            .mapNotNull { root -> root.findFolder(kobwebProject.publicPath) }
    }

    // Kobweb only copies public files from libraries that are Kobweb libraries. Their metadata doesn't say which public
    // path they were built with, so assume Kobweb's default
    val libraryFolders = OrderEnumerator.orderEntries(module).recursively().librariesOnly().classes().roots
        .filter { root -> root.findFileByRelativePath(KOBWEB_METADATA_MODULE) != null }
        .mapNotNull { root -> root.findFolder(KobwebModel.DEFAULT_PUBLIC_PATH) }

    return moduleFolders + libraryFolders
}

/**
 * Returns the file (or folder) that [path] leads to in a public folder (see [findPublicFolders]), e.g.
 * `src/jsMain/resources/public/resume.pdf` for "/resume.pdf".
 *
 * Returns null if there's no such file, or if the path has segments that aren't known until runtime.
 */
fun PsiElement.findPublicResource(path: LinkPath): VirtualFile? = findPublicResource(path.segments)

/**
 * Like `findPublicResource(LinkPath)`, but for the path made of just these [segments], e.g. the first segment of
 * "/images/logo.png" leads to the folder `src/jsMain/resources/public/images`.
 */
fun PsiElement.findPublicResource(segments: List<LinkPath.Segment>): VirtualFile? {
    val relativePath = segments.map { segment -> segment.text ?: return null }.joinToString("/")
    return findPublicFolders().firstNotNullOfOrNull { folder -> folder.findFileByRelativePath(relativePath) }
}
