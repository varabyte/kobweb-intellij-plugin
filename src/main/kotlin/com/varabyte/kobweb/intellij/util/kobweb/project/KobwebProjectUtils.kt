package com.varabyte.kobweb.intellij.util.kobweb.project

import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import org.jetbrains.plugins.gradle.util.GradleUtil

/**
 * Given a PSI element that represents a piece of code inside a Kotlin dependency, fetch its containing klib.
 *
 * This will work even if the PSI element is contained inside a sources jar, assuming it is a sibling to the klib.
 */
private val PsiElement.containingKlib: VirtualFile?
    get() {
        /**
         * Given a virtual file that points at a sources jar, return its sibling klib file.
         */
        fun VirtualFile.sourcesJarToKlib(): VirtualFile? {
            // The virtual file representing the jar is rooted at the jar itself; jar.parent would return null.
            // Therefore, we have to escape out of the virtual file ecosystem and into the IO file system to escape. Once
            // out, it's easy to find a sibling file because we can access the parent directory.
            val ioFile = VfsUtilCore.virtualToIoFile(this)
            val nameWithoutExtension = ioFile.nameWithoutExtension.removeSuffix("-sources")

            val klibFile = ioFile.parentFile.resolve("$nameWithoutExtension.klib")
            return klibFile.toVirtualFile()?.let { JarFileSystem.getInstance().getJarRootForLocalFile(it) }
        }

        var currFile: VirtualFile? = this.containingFile.virtualFile
        while (currFile != null) {
            if (currFile.extension == "jar") {
                currFile = currFile.sourcesJarToKlib()
                break
            } else if (currFile.extension == "klib") {
                break
            }

            currFile = currFile.parent
        }

        return currFile
    }

/**
 * Returns the Kobweb project associated with the owning context of this element, or null if none is found.
 *
 * Kobweb project information can be associated with both local modules and third-party artifacts (e.g. maven
 * dependencies).
 */
fun PsiElement.findKobwebProject(): KobwebProject? {
    val kobwebProjects = project.service<KobwebProjectCacheService>()
    return ModuleUtil.findModuleForPsiElement(this)?.let { elementModule ->
        // Note that this module is likely a specific source submodule of the module we want (the one associated with
        // the Gradle build script). That is, we are probably getting "app.site.jsMain" when we want "app.site"
        @Suppress("UnstableApiUsage") // "findGradleModuleData" has been experimental for 5 years...
        GradleUtil.findGradleModuleData(elementModule)?.let { moduleDataNode ->
            GradleUtil.findGradleModule(this.project, moduleDataNode.data)?.let { gradleModule ->
                kobwebProjects[gradleModule]
            }
        }
    } ?: this.containingKlib?.let { kobwebProjects[it] }
}
