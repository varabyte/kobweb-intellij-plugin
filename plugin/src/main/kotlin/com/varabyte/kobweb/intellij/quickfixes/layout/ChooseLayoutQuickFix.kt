package com.varabyte.kobweb.intellij.quickfixes.layout

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.project.Project
import com.varabyte.kobweb.intellij.util.kobweb.layout.LayoutPathChooser
import com.varabyte.kobweb.intellij.util.ux.UxGlobals

/**
 * Fix an invalid `@Layout` annotation by choosing a layout from a popup.
 *
 * This works both on the annotation's path (replacing it) and on an annotation without one (adding it).
 *
 * @see LayoutPathChooser
 */
class ChooseLayoutQuickFix : ModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.choose_layout.name")

    override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
        return LayoutPathChooser(descriptor.psiElement).choose()
    }
}
