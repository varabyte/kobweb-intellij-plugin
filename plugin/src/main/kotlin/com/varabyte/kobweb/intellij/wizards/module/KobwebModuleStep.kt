package com.varabyte.kobweb.intellij.wizards.module

import com.intellij.ide.wizard.AbstractNewProjectWizardMultiStepBase
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.layout.ValidationInfoBuilder
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * The step of the Kobweb wizard that asks which kind of module to create, showing that kind's options below it.
 *
 * It also holds the options that all kinds share, so they keep their values when users switch between kinds. The
 * module's name and location come from the [NewProjectWizardBaseStep] before it.
 */
class KobwebModuleStep(
    private val baseStep: NewProjectWizardBaseStep,
) : AbstractNewProjectWizardMultiStepBase(baseStep) {
    override val label = UxGlobals.textBundle.message("wizard.kobweb.module.type")

    internal val moduleNameProperty get() = baseStep.nameProperty
    internal val groupProperty = propertyGraph.lazyProperty(::suggestGroup)
    internal val useSilkProperty = propertyGraph.property(true)
    internal val useServerProperty = propertyGraph.property(false)
    internal val useMarkdownProperty = propertyGraph.property(true)
    internal val generateReadmeProperty =
        propertyGraph.property(true).bindBooleanStorage(GENERATE_README_PROPERTY_NAME)
    internal val addSampleCodeProperty =
        propertyGraph.property(true).bindBooleanStorage(ADD_SAMPLE_CODE_PROPERTY_NAME)

    init {
        // Follows the module name and location until the user edits it
        groupProperty.dependsOn(baseStep.nameProperty, ::suggestGroup)
        groupProperty.dependsOn(baseStep.pathProperty, ::suggestGroup)
    }

    private val moduleDir: Path get() = Path(baseStep.path, baseStep.name)

    override fun initSteps(): Map<String, NewProjectWizardStep> {
        val bundle = UxGlobals.textBundle
        return linkedMapOf(
            bundle.message("wizard.kobweb.module.type.app") to KobwebAppStep(this),
            bundle.message("wizard.kobweb.module.type.library") to KobwebLibraryStep(this),
            bundle.message("wizard.kobweb.module.type.worker") to KobwebWorkerStep(this),
        )
    }

    override fun setupUI(builder: Panel) {
        val bundle = UxGlobals.textBundle
        val gradleRoot = context.project?.findGradleRoot(moduleDir)
        when {
            context.isCreatingNewProject -> bundle.message("wizard.kobweb.project.unsupported")
            gradleRoot == null -> bundle.message("wizard.kobweb.module.error.not.gradle")
            else -> null
        }?.let { message ->
            builder.row { label(message).validationOnApply { error(message) } }
            return
        }
        // If gradleRoot were null, we would have returned above. Kotlin code analysis doesn't seem to detect this.
        gradleRoot!!

        super.setupUI(builder)
        if (gradleRoot.findFileByRelativePath("gradle/libs.versions.toml") == null) {
            builder.row { comment(bundle.message("wizard.kobweb.module.catalog.created")) }
        }
    }

    /**
     * Adds the group field, whose error message uses [exampleGroup] (e.g. `com.example.mylib`) as an example.
     */
    internal fun Panel.groupRow(exampleGroup: String) {
        val bundle = UxGlobals.textBundle
        row(bundle.message("wizard.kobweb.module.group")) {
            textField()
                .bindText(groupProperty)
                .columns(COLUMNS_MEDIUM)
                .validationOnInput { field -> validateGroup(field.text, exampleGroup) }
                .validationOnApply { field -> validateGroup(field.text, exampleGroup) }
                // The location field belongs to an earlier step, so check here that it's still inside the build
                .validationOnApply {
                    if (context.project?.findGradleRoot(moduleDir) != null) null
                    else error(bundle.message("wizard.kobweb.module.error.not.gradle"))
                }
        }
    }

    /** Adds the checkboxes for the Kobweb features that all kinds of modules can use. */
    internal fun Panel.featureRows() {
        val bundle = UxGlobals.textBundle
        row {
            checkBox(bundle.message("wizard.kobweb.module.use.silk"))
                .bindSelected(useSilkProperty)
                .comment(bundle.message("wizard.kobweb.module.use.silk.comment"))
        }
        row {
            checkBox(bundle.message("wizard.kobweb.module.use.server"))
                .bindSelected(useServerProperty)
                .comment(bundle.message("wizard.kobweb.module.use.server.comment"))
        }
        row {
            checkBox(bundle.message("wizard.kobweb.module.use.markdown"))
                .bindSelected(useMarkdownProperty)
                .comment(bundle.message("wizard.kobweb.module.use.markdown.comment"))
        }
    }

    /** Adds a titled section that separates the Kobweb-specific options from the general ones above it. */
    internal fun Panel.kobwebConfigurationGroup(init: Panel.() -> Unit) {
        group(UxGlobals.textBundle.message("wizard.kobweb.module.configuration"), init = init)
            .topGap(TopGap.MEDIUM)
    }

    internal fun Panel.readmeRow() {
        row {
            checkBox(UxGlobals.textBundle.message("wizard.kobweb.module.generate.readme"))
                .bindSelected(generateReadmeProperty)
        }
    }

    internal fun Panel.sampleCodeRow() {
        row {
            checkBox(UxGlobals.textBundle.message("wizard.kobweb.module.add.sample.code"))
                .bindSelected(addSampleCodeProperty)
        }
    }

    /**
     * Writes the new module with [write], which gets a path relative to the build's root (e.g. `"site"`) and returns
     * the module's build script. Then syncs Gradle and opens the build script.
     */
    internal fun writeModule(project: Project, write: KobwebModuleWriter.(moduleDir: String) -> VirtualFile) {
        val gradleRoot = project.findGradleRoot(moduleDir) ?: return
        val relativeModuleDir = gradleRoot.toNioPath().relativize(moduleDir).invariantSeparatorsPathString

        val buildFile = WriteCommandAction.writeCommandAction(project)
            .withName(UxGlobals.textBundle.message("wizard.kobweb.module.command"))
            .compute<VirtualFile, Throwable> {
                KobwebModuleWriter(project, gradleRoot).write(relativeModuleDir)
            }

        ExternalSystemUtil.refreshProject(gradleRoot.path, ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
        OpenFileDescriptor(project, buildFile).navigate(/* requestFocus = */ true)
    }

    /**
     * Suggests a group for the new module.
     *
     * Generally, this will be the project's group plus the module's name, e.g. `com.example.mylib` for a module named
     * `"mylib"` in a codebase whose root group is `com.example`.
     *
     * If a root group can't be detected, we iterate through all Kobweb projects in this codebase and try to determine
     * a common shared group from all of them. If nothing can be found, the final suggestion will just be the new
     * module's name.
     */
    private fun suggestGroup(): String {
        val project = context.project
        val gradleRoot = project?.findGradleRoot(moduleDir)
        val buildGroup = if (project != null && gradleRoot != null) {
            val buildModules = ModuleManager.getInstance(project).modules
                .filter { it.externalProperties.getRootProjectPath() == gradleRoot.path }
            val rootGroup = buildModules
                .filter { it.externalProperties.getLinkedProjectPath() == gradleRoot.path }
                .firstNotNullOfOrNull { it.externalProperties.getExternalModuleGroup()?.takeIf(String::isNotEmpty) }
            rootGroup ?: buildModules
                .mapNotNull { (it.findKobwebProject()?.source as? KobwebProject.Source.Local)?.module }
                .distinct()
                .mapNotNull { it.groupWithoutOwnName() }
                .distinct()
                .commonGroupPrefix()
        } else null
        val modulePackage = baseStep.name.lowercase().filter { it.isLetterOrDigit() }.takeIf { it.isNotEmpty() }
        return listOfNotNull(buildGroup, modulePackage).joinToString(".")
    }

    private companion object {
        // These options are remembered across projects, as IntelliJ does for its own "Add sample code" option
        const val GENERATE_README_PROPERTY_NAME = "kobweb.wizard.module.generateReadme"
        const val ADD_SAMPLE_CODE_PROPERTY_NAME = "kobweb.wizard.module.addSampleCode"
    }
}

private val Module.externalProperties get() = ExternalSystemModulePropertyManager.getInstance(this)

/**
 * This Gradle module's group, minus the trailing segment if it matches its own name (e.g. `chat` for `chat.site`).
 *
 * The reason for this is if we have a codebase which only has a single project, we still want to try to determine the
 * best common group. If a module is called, say, `"common"` but its group is `com.example`, we'll still just return
 * `com.example`.
 */
private fun Module.groupWithoutOwnName(): String? {
    val group = externalProperties.getExternalModuleGroup()?.takeIf { it.isNotEmpty() } ?: return null
    // Gradle IDs end with the project's name, e.g. ":site" or ":libs:site"
    val name = externalProperties.getLinkedProjectId()?.substringAfterLast(':') ?: return group
    return group.removeSuffix(".$name")
}

/**
 * The leading segments that all of these groups share, e.g. `com.example` for `com.example.auth` and
 * `com.example.site`.
 */
private fun List<String>.commonGroupPrefix(): String? {
    val segments = map { it.split('.') }
    val common = segments.firstOrNull()?.indices
        ?.takeWhile { i -> segments.all { it.getOrNull(i) == segments[0][i] } }
        ?.map { segments[0][it] }
        .orEmpty()
    return common.joinToString(".").takeIf { it.isNotEmpty() }
}

private fun ValidationInfoBuilder.validateGroup(group: String, exampleGroup: String): ValidationInfo? =
    if (group.isNotEmpty() && group.split('.').all { StringUtil.isJavaIdentifier(it) }) null
    else error(UxGlobals.textBundle.message("wizard.kobweb.module.error.invalid.group", exampleGroup))

/** The root directory of the Gradle build that [moduleDir] is inside of, or null if it isn't inside one. */
private fun Project.findGradleRoot(moduleDir: Path): VirtualFile? =
    GradleSettings.getInstance(this).linkedProjectsSettings
        .map { Path(it.externalProjectPath) }
        .filter { moduleDir.startsWith(it) && moduleDir != it }
        .maxByOrNull { it.nameCount }
        ?.let { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
