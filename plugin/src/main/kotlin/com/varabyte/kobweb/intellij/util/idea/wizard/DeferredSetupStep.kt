package com.varabyte.kobweb.intellij.util.idea.wizard

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project

/**
 * Shows [step] as usual, but sets up the project with it only when [setupProjectLater] is called.
 *
 * Useful when a step must run after a later step's setup, e.g. creating a Git repository only after the files to
 * commit are written.
 */
class DeferredSetupStep(private val step: NewProjectWizardStep) : NewProjectWizardStep by step {
    override fun setupProject(project: Project) {}

    fun setupProjectLater(project: Project) = step.setupProject(project)
}
