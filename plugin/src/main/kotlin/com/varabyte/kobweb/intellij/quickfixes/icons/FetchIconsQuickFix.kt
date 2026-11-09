package com.varabyte.kobweb.intellij.quickfixes.icons

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.varabyte.kobweb.intellij.util.kobweb.icons.fetchIcons
import com.varabyte.kobweb.intellij.util.ux.UxGlobals

/**
 * Fetch the data for the icon sets declared in a build script, by running Kobweb's `kobwebFetchIcons` task.
 */
class FetchIconsQuickFix : LocalQuickFix {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.fetch_icons.name")

    // Running a Gradle task doesn't change any code
    override fun startInWriteAction() = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        descriptor.psiElement?.containingFile?.fetchIcons()
    }
}
