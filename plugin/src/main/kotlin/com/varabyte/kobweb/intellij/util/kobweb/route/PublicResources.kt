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
 * Returns the file (or folder) that [path] leads to in a public folder, e.g. `src/jsMain/resources/public/resume.pdf`
 * for "/resume.pdf", which Kobweb serves as is.
 *
 * This checks the resources of this element's module plus any Kobweb modules it depends on, as well as any Kobweb
 * libraries it depends on (whose public files Kobweb copies into the site). Returns null if there's no such file, or if
 * the path has segments that aren't known until runtime.
 */
fun PsiElement.findPublicResource(path: LinkPath): VirtualFile? {
    val module = module ?: return null
    if (path.segments.any { it == null }) return null
    fun VirtualFile.findPublicFile(publicPath: String) =
        findFileByRelativePath((listOf(publicPath.trim('/')) + path.segments).joinToString("/"))

    val modules = buildList {
        OrderEnumerator.orderEntries(module).recursively().withoutSdk().withoutLibraries().productionOnly()
            .forEachModule { dependency -> add(dependency); true }
    }
    val moduleFile = modules.firstNotNullOfOrNull { dependency ->
        // Kobweb only serves public files from Kobweb projects
        val kobwebProject = dependency.findKobwebProject() ?: return@firstNotNullOfOrNull null
        // Kotlin multiplatform modules mark their resources with Kotlin's own root type
        ModuleRootManager.getInstance(dependency)
            .getSourceRoots(setOf(JavaResourceRootType.RESOURCE, ResourceKotlinRootType))
            .firstNotNullOfOrNull { root -> root.findPublicFile(kobwebProject.publicPath) }
    }
    if (moduleFile != null) return moduleFile

    // Kobweb only copies public files from libraries that are Kobweb libraries. Their metadata doesn't say which public
    // path they were built with, so assume Kobweb's default
    return OrderEnumerator.orderEntries(module).recursively().librariesOnly().classes().roots
        .filter { root -> root.findFileByRelativePath(KOBWEB_METADATA_MODULE) != null }
        .firstNotNullOfOrNull { root -> root.findPublicFile(KobwebModel.DEFAULT_PUBLIC_PATH) }
}
