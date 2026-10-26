package com.varabyte.kobweb.intellij.util.kobweb.conf

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.varabyte.kobweb.intellij.services.conf.KobwebConfReader
import org.jetbrains.kotlin.idea.base.util.module

/** Where a Kobweb site keeps its configuration, relative to its Gradle project's folder. */
private const val KOBWEB_CONF_PATH = ".kobweb/conf.yaml"

/**
 * Returns the `.kobweb/conf.yaml` file that configures the Kobweb site this element belongs to (e.g. its title and
 * server settings), or null if there isn't one (e.g. if it's in a library).
 *
 * See [KobwebConfReader] for reading its values.
 */
fun PsiElement.findKobwebConf(): PsiFile? {
    val module = module ?: return null
    // The file lives in the Gradle project's folder, which source set modules (e.g. "site.jsMain") also know about.
    // Modules that weren't imported from Gradle (e.g. in tests) only have their content roots to go on
    val projectFolder = ExternalSystemApiUtil.getExternalProjectPath(module)
        ?.let { path -> LocalFileSystem.getInstance().findFileByPath(path) }
    val folders = listOfNotNull(projectFolder) + ModuleRootManager.getInstance(module).contentRoots
    val file = folders.firstNotNullOfOrNull { folder -> folder.findFileByRelativePath(KOBWEB_CONF_PATH) } ?: return null
    return PsiManager.getInstance(project).findFile(file)
}
