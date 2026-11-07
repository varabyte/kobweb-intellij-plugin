package com.varabyte.kobweb.intellij.wizards.module

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter
import com.intellij.ide.wizard.GitNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.varabyte.kobweb.intellij.util.idea.wizard.DeferredSetupStep
import com.varabyte.kobweb.intellij.util.ux.UxGlobals

/** The "Kobweb" entry of IntelliJ's New Project and New Module dialogs. */
class KobwebNewProjectWizard : GeneratorNewProjectWizard {
    override val id = "kobweb"
    override val name = UxGlobals.FAMILY_NAME
    override val icon = UxGlobals.gutterIcon
    override val description = UxGlobals.textBundle.message("wizard.kobweb.description")

    override fun createStep(context: WizardContext): NewProjectWizardStep =
        RootNewProjectWizardStep(context)
            .nextStep(::NewProjectWizardBaseStep)
            // The Git step only shows its "Create Git repository" checkbox when creating a new project. It creates the
            // repository once the project opens, so it's deferred until the Kobweb step has written its files then.
            .nextStep { baseStep ->
                DeferredSetupStep(GitNewProjectWizardStep(baseStep)).nextStep { KobwebModuleStep(it, baseStep) }
            }

    /** Registers this wizard in both the New Project and New Module dialogs. */
    class Builder : GeneratorNewProjectWizardBuilderAdapter(KobwebNewProjectWizard())
}
