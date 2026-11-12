package com.varabyte.kobweb.intellij.wizards.module

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.varabyte.kobweb.intellij.util.ux.UxGlobals

/** The options for a new Kobweb app module, shown when users pick that kind of module. */
class KobwebAppStep(private val parent: KobwebModuleStep) : AbstractNewProjectWizardStep(parent) {
    private val titleProperty = propertyGraph.lazyProperty(::suggestTitle)

    init {
        // Follows the module name until the user edits it
        titleProperty.dependsOn(parent.moduleNameProperty, ::suggestTitle)
    }

    override fun setupUI(builder: Panel) {
        val bundle = UxGlobals.textBundle
        with(parent) {
            builder.groupRow(exampleGroup = "com.example.mysite")
            builder.readmeRow()
            builder.sampleCodeRow()
            builder.kobwebConfigurationGroup {
                row(bundle.message("wizard.kobweb.module.title")) {
                    textField()
                        .bindText(titleProperty)
                        .columns(COLUMNS_MEDIUM)
                        .comment(bundle.message("wizard.kobweb.module.title.comment"))
                        .validationOnApply { field ->
                            if (field.text.isNotBlank()) null
                            else error(bundle.message("wizard.kobweb.module.error.empty.title"))
                        }
                }
                featureRows()
            }
        }
    }

    override fun setupProject(project: Project) {
        val options = KobwebAppOptions(
            group = parent.groupProperty.get(),
            title = titleProperty.get(),
            useSilk = parent.useSilkProperty.get(),
            iconSet = parent.selectedIconSet,
            useServer = parent.useServerProperty.get(),
            useMarkdown = parent.useMarkdownProperty.get(),
            generateReadme = parent.generateReadmeProperty.get(),
            addSampleCode = parent.addSampleCodeProperty.get(),
        )
        // An application is the end of the dependency chain: other modules never depend on it.
        parent.writeModule(project, dependents = emptyList(), includeJvm = false) { moduleDir ->
            writeApp(moduleDir, options)
        }
    }

    /** Suggests a title from the module's name, the way `kobweb create` does (e.g. "My Site" for `my-site`). */
    private fun suggestTitle(): String =
        parent.moduleNameProperty.get()
            .split(Regex("""\W"""))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
}
