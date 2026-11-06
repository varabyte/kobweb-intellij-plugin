package com.varabyte.kobweb.intellij.wizards.module

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel

/** The options for a new Kobweb library module, shown when users pick that kind of module. */
class KobwebLibraryStep(private val parent: KobwebModuleStep) : AbstractNewProjectWizardStep(parent) {
    override fun setupUI(builder: Panel) {
        with(parent) {
            builder.groupRow(exampleGroup = "com.example.mylib")
            builder.readmeRow()
            builder.sampleCodeRow()
            builder.kobwebConfigurationGroup {
                featureRows()
            }
            builder.dependentsRow()
        }
    }

    override fun setupProject(project: Project) {
        val options = KobwebLibraryOptions(
            group = parent.groupProperty.get(),
            useSilk = parent.useSilkProperty.get(),
            useServer = parent.useServerProperty.get(),
            useMarkdown = parent.useMarkdownProperty.get(),
            generateReadme = parent.generateReadmeProperty.get(),
            addSampleCode = parent.addSampleCodeProperty.get(),
        )
        parent.writeModule(project, parent.selectedDependents, includeJvm = options.useServer) { moduleDir ->
            writeLibrary(moduleDir, options)
        }
    }
}
