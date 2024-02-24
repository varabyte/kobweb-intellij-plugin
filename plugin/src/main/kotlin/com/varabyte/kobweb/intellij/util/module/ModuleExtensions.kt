package com.varabyte.kobweb.intellij.util.module

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
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
    @Suppress("UnstableApiUsage") // "findGradleModuleData" has been experimental for 5 years...
    return GradleUtil.findGradleModuleData(this)?.let { moduleDataNode ->
        GradleUtil.findGradleModule(this.project, moduleDataNode.data)
    }
}
