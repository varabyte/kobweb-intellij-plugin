package com.varabyte.kobweb.intellij.wizards.module

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.layout.ValidationInfoBuilder
import com.varabyte.kobweb.intellij.util.idea.swing.installCodeTooltip
import com.varabyte.kobweb.intellij.util.ux.UxGlobals

/** The options for a new Kobweb worker module, shown when users pick that kind of module. */
class KobwebWorkerStep(private val parent: KobwebModuleStep) : AbstractNewProjectWizardStep(parent) {
    private val workerNameProperty = propertyGraph.lazyProperty(::suggestName)
    private val useCustomTypesProperty = propertyGraph.property(true)

    init {
        // Follows the module name until the user edits it
        workerNameProperty.dependsOn(parent.moduleNameProperty, ::suggestName)
    }

    override fun setupUI(builder: Panel) {
        val bundle = UxGlobals.textBundle
        with(parent) {
            builder.groupRow(exampleGroup = "com.example.mysite.worker")
            builder.readmeRow()
            builder.kobwebConfigurationGroup {
                row(bundle.message("wizard.kobweb.module.worker.name")) {
                    textField()
                        .bindText(workerNameProperty)
                        .columns(COLUMNS_MEDIUM)
                        .applyToComponent { emptyText.text = bundle.message("wizard.kobweb.module.worker.name.hint") }
                        .comment(bundle.message("wizard.kobweb.module.worker.name.comment"))
                        .validationOnInput { field -> validateName(field.text) }
                        .validationOnApply { field -> validateName(field.text) }
                }
                buttonsGroup(bundle.message("wizard.kobweb.module.worker.types")) {
                    row {
                        radioButton(bundle.message("wizard.kobweb.module.worker.types.strings"), false)
                            .installCodeTooltip(
                                context.project,
                                """
                                class EchoWorkerFactory : WorkerFactory<String, String>

                                worker.postInput("Hello")
                                """.trimIndent(),
                            )
                        radioButton(bundle.message("wizard.kobweb.module.worker.types.custom"), true)
                            .installCodeTooltip(
                                context.project,
                                """
                                @Serializable
                                data class EchoInput(val message: String)

                                class EchoWorkerFactory : WorkerFactory<EchoInput, EchoOutput>

                                worker.postInput(EchoInput("Hello"))
                                """.trimIndent(),
                            )
                    }.rowComment(bundle.message("wizard.kobweb.module.worker.types.comment"))
                }.bind({ useCustomTypesProperty.get() }, { useCustomTypesProperty.set(it) })
            }
        }
    }

    override fun setupProject(project: Project) {
        val options = KobwebWorkerOptions(
            group = parent.groupProperty.get(),
            name = workerNameProperty.get(),
            useCustomTypes = useCustomTypesProperty.get(),
            generateReadme = parent.generateReadmeProperty.get(),
        )
        parent.writeModule(project) { moduleDir -> writeWorker(moduleDir, options) }
    }

    /**
     * Suggests a worker name from the module's name, e.g. `ImageProcessor` for `image-processor` or
     * `image-processor-worker`.
     *
     * A module that's just called `worker` gets no suggestion, so users pick a name that says what the worker does.
     */
    private fun suggestName(): String {
        val pascalName = parent.moduleNameProperty.get()
            .split(Regex("""[\W_]"""))
            .filter { it.isNotBlank() }
            .joinToString("") { word -> word.replaceFirstChar { it.titlecase() } }
        return pascalName.removeSuffix("Worker")
    }

    private fun ValidationInfoBuilder.validateName(name: String) = when {
        !StringUtil.isJavaIdentifier(name) ->
            error(UxGlobals.textBundle.message("wizard.kobweb.module.worker.error.invalid.name"))
        // Kobweb appends "WorkerFactory" and "Worker" to the name, so these would double up, e.g. `WorkerWorkerFactory`
        name == "Worker" ->
            warning(UxGlobals.textBundle.message("wizard.kobweb.module.worker.warning.worker.name", name))
        name.endsWith("Worker") ->
            warning(
                UxGlobals.textBundle.message(
                    "wizard.kobweb.module.worker.warning.worker.suffix",
                    name,
                    name.removeSuffix("Worker"),
                )
            )
        else -> null
    }
}
