package com.varabyte.kobweb.intellij.quickfixes.route

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.modcommand.ModDeleteFile
import com.intellij.openapi.project.Project
import com.varabyte.kobweb.intellij.util.ux.UxGlobals

/**
 * Delete a file whose only purpose is a `@file:PackageMapping` annotation that maps a package to the name Kobweb would
 * use for it anyway.
 *
 * Only offer this when the annotation is all the file contains. Otherwise, see [RemovePackageMappingQuickFix].
 */
class DeletePackageMappingFileQuickFix : ModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.delete_package_mapping_file.name")

    override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
        val file = descriptor.psiElement.containingFile?.virtualFile ?: return ModCommand.nop()
        return ModDeleteFile(file)
    }
}
