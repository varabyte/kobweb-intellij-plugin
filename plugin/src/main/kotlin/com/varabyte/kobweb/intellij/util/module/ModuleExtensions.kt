package com.varabyte.kobweb.intellij.util.module

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil

/**
 * Given an IntelliJ module, return the associated module that represents the root of a Gradle project.
 *
 * Often, a module you fetch for a [PsiElement] is the one associated with a source directory, but what we often
 * actually want is its parent module. That is, instead of the module "app.site.jsMain" we want "app.site".
 *
 * If found, the module returned will be home to a Gradle build file, and you can be confident it represents the
 * root of a Gradle project.
 */
fun Module.toGradleModule(): Module? {
    // Make a copy of GradleUtil.findGradleModuleData to avoid getting hit with an experimental API warning
    fun findGradleModuleData(module: Module): DataNode<ModuleData>? {
        return ExternalSystemApiUtil.getExternalProjectPath(module)?.let { projectPath ->
            ExternalSystemApiUtil.findModuleNode(module.project, GradleConstants.SYSTEM_ID, projectPath)
        }
    }

    return findGradleModuleData(this)?.let { moduleDataNode ->
        GradleUtil.findGradleModule(this.project, moduleDataNode.data)
    }
}
