package com.varabyte.kobweb.intellij.wizards.module

import com.intellij.ide.wizard.AbstractNewProjectWizardMultiStepBase
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.ui.JBUI
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.notification.KobwebNotifier
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
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

    /**
     * Whether other existing application and/or library modules should depend on the new module.
     *
     * A library is never selected by default, since chaining Kobweb libraries is fairly uncommon. Meanwhile, an
     * application is selected only if it's the only one in the build. This way, users never have to uncheck more than
     * a single box if they want to continue without any dependents.
     */
    private val dependentProperties: Map<DependentModule, GraphProperty<Boolean>> by lazy {
        val dependents = findDependents()
        val onlyApplication = dependents.singleOrNull { it.type == KobwebProjectType.Application }
        dependents.associateWith { propertyGraph.property(it == onlyApplication) }
    }

    init {
        // Follows the selected type until the user edits it, e.g. `site` for an application
        baseStep.nameProperty.dependsOn(stepProperty, ::suggestModuleName)
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
     * Adds a row for choosing which of the build's applications and libraries should depend on the new module: a
     * checkbox if there's just one of them, or otherwise a link that opens a list of them. Adds nothing if there are
     * none.
     *
     * It always takes a single row, so builds with many modules don't vertically stretch the wizard.
     */
    internal fun Panel.dependentsRow() {
        val bundle = UxGlobals.textBundle
        val single = dependentProperties.entries.singleOrNull()
        if (single != null) {
            row {
                checkBox(bundle.message("wizard.kobweb.module.add.as.dependency.of", single.key.gradlePath))
                    .bindSelected(single.value)
            }.topGap(TopGap.MEDIUM)
        } else if (dependentProperties.isNotEmpty()) {
            row(bundle.message("wizard.kobweb.module.add.as.dependency")) {
                // The popup is rebuilt from the properties on each click, so it always shows the current choice
                val link = DropDownLink(dependentsSummary()) { createDependentsPopup() }
                for (property in dependentProperties.values) {
                    property.afterChange { link.text = dependentsSummary() }
                }
                cell(link)
            }.topGap(TopGap.MEDIUM)
        }
    }

    /** A short description of the chosen dependents, e.g. `:site, :auth, and 3 more`. */
    private fun dependentsSummary(): String {
        val bundle = UxGlobals.textBundle
        val paths = selectedDependents.map { it.gradlePath }
        return when {
            paths.isEmpty() -> bundle.message("wizard.kobweb.module.dependents.none")
            paths.size <= MAX_SUMMARIZED_DEPENDENTS -> paths.joinToString(", ")
            else -> bundle.message(
                "wizard.kobweb.module.dependents.more",
                paths.take(MAX_SUMMARIZED_DEPENDENTS).joinToString(", "),
                paths.size - MAX_SUMMARIZED_DEPENDENTS,
            )
        }
    }

    private fun createDependentsPopup(): JBPopup {
        val bundle = UxGlobals.textBundle
        val list = CheckBoxList<DependentModule>()
        for ((dependent, property) in dependentProperties) {
            val typeKey = when (dependent.type) {
                KobwebProjectType.Application -> "wizard.kobweb.module.dependent.application"
                else -> "wizard.kobweb.module.dependent.library"
            }
            list.addItem(dependent, bundle.message(typeKey, dependent.gradlePath), property.get())
        }
        list.setCheckBoxListListener { index, selected ->
            list.getItemAt(index)?.let { dependentProperties.getValue(it).set(selected) }
        }
        list.visibleRowCount = dependentProperties.size.coerceAtMost(MAX_VISIBLE_DEPENDENTS)
        // Without this, the longest label ends right at the popup's edge
        list.border = JBUI.Borders.empty(4, 4, 4, 12)
        ListSpeedSearch.installOn(list) { it.text }
        return JBPopupFactory.getInstance()
            .createComponentPopupBuilder(ScrollPaneFactory.createScrollPane(list, /* withoutBorder = */ true), list)
            .setRequestFocus(true)
            .createPopup()
    }

    /** The modules chosen in [dependentsRow]. */
    internal val selectedDependents: List<DependentModule>
        get() = dependentProperties.filterValues { it.get() }.keys.toList()

    /**
     * Writes the new module with [write], which gets a path relative to the build's root (e.g. `"site"`) and returns
     * the module's build script, and adds it as a dependency of [dependents]. Then syncs Gradle and opens the build
     * script.
     *
     * @param includeJvm Whether the new module has a JVM target, so it's also added to the dependents' `jvmMain`
     *   dependencies.
     */
    internal fun writeModule(
        project: Project,
        dependents: List<DependentModule>,
        includeJvm: Boolean,
        write: KobwebModuleWriter.(moduleDir: String) -> VirtualFile,
    ) {
        val gradleRoot = project.findGradleRoot(moduleDir) ?: return
        val relativeModuleDir = gradleRoot.toNioPath().relativize(moduleDir).invariantSeparatorsPathString
        val writer = KobwebModuleWriter(project, gradleRoot)

        val result = WriteCommandAction.writeCommandAction(project)
            .withName(UxGlobals.textBundle.message("wizard.kobweb.module.command"))
            .compute<WriteModuleResult, Throwable> {
                WriteModuleResult(
                    buildFile = writer.write(relativeModuleDir),
                    skippedDependentDirs = writer.addAsDependency(
                        relativeModuleDir,
                        dependents.map { it.dir },
                        includeJvm,
                    ),
                )
            }

        ExternalSystemUtil.refreshProject(gradleRoot.path, ImportSpecBuilder(project, GradleConstants.SYSTEM_ID))
        OpenFileDescriptor(project, result.buildFile).navigate(/* requestFocus = */ true)

        val gradlePath = writer.gradlePathOf(relativeModuleDir)
        for (dependent in dependents.filter { it.dir in result.skippedDependentDirs }) {
            notifyDependencyNotAdded(project, writer, dependent, gradlePath)
        }
    }

    private fun notifyDependencyNotAdded(
        project: Project,
        writer: KobwebModuleWriter,
        dependent: DependentModule,
        gradlePath: String,
    ) {
        val bundle = UxGlobals.textBundle
        val builder = KobwebNotifier.Builder(
            bundle.message("wizard.kobweb.module.dependency.not.added", dependent.gradlePath, gradlePath)
        ).type(NotificationType.WARNING)
        val dependentBuildFile = writer.findBuildFile(dependent.dir)
        if (dependentBuildFile != null) {
            val actionText = bundle.message(
                "wizard.kobweb.module.dependency.not.added.open",
                dependentBuildFile.name,
                dependent.gradlePath,
            )
            builder.addAction(actionText) {
                OpenFileDescriptor(project, dependentBuildFile).navigate(/* requestFocus = */ true)
            }
        }
        builder.notify(project)
    }

    /**
     * Suggests a name for the new module based on its type, numbered if a module or directory already uses it (e.g.
     * `site1` when there's already a `site`).
     */
    private fun suggestModuleName(): String {
        val defaultName = when (steps[step]) {
            is KobwebAppStep -> "site"
            is KobwebLibraryStep -> "library"
            is KobwebWorkerStep -> "worker"
            else -> baseStep.defaultName
        }
        // The base step suggests a new name from this one when the location changes
        baseStep.defaultName = defaultName

        val moduleNames = context.project?.let { project ->
            ModuleManager.getInstance(project).modules.map { it.name }.toSet()
        }.orEmpty()
        return FileUtil.createSequentFileName(Path(baseStep.path).toFile(), defaultName, "") { file ->
            !file.exists() && file.name !in moduleNames
        }
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

    /** The build's Kobweb applications and libraries, applications first. */
    private fun findDependents(): List<DependentModule> {
        val project = context.project ?: return emptyList()
        val gradleRoot = project.findGradleRoot(moduleDir) ?: return emptyList()
        return ModuleManager.getInstance(project).modules
            .asSequence()
            .filter { it.externalProperties.getRootProjectPath() == gradleRoot.path }
            .mapNotNull { it.findKobwebProject() }
            .filter { it.type == KobwebProjectType.Application || it.type == KobwebProjectType.Library }
            .mapNotNull { kobwebProject ->
                val module = (kobwebProject.source as? KobwebProject.Source.Local)?.module ?: return@mapNotNull null
                val gradlePath = GradleProjectResolverUtil.getGradleIdentityPathOrNull(module) ?: return@mapNotNull null
                val dir = module.externalProperties.getLinkedProjectPath() ?: return@mapNotNull null
                DependentModule(
                    gradlePath,
                    gradleRoot.toNioPath().relativize(Path(dir)).invariantSeparatorsPathString,
                    kobwebProject.type,
                )
            }
            .distinct()
            .sortedWith(compareBy({ it.type != KobwebProjectType.Application }, { it.gradlePath }))
            .toList()
    }

    /**
     * The result of [writeModule]'s write command.
     *
     * @property skippedDependentDirs The dependents whose build script couldn't be edited.
     */
    private class WriteModuleResult(val buildFile: VirtualFile, val skippedDependentDirs: List<String>)

    private companion object {
        /** The popup of dependents scrolls past this many. */
        const val MAX_VISIBLE_DEPENDENTS = 10

        /** The dependents row names this many of the chosen dependents, then counts the rest. */
        const val MAX_SUMMARIZED_DEPENDENTS = 2

        // These options are remembered across projects, as IntelliJ does for its own "Add sample code" option
        const val GENERATE_README_PROPERTY_NAME = "kobweb.wizard.module.generateReadme"
        const val ADD_SAMPLE_CODE_PROPERTY_NAME = "kobweb.wizard.module.addSampleCode"
    }
}

/**
 * An application or library in the build that can depend on the new module.
 *
 * @property gradlePath e.g. `:site`
 * @property dir The module's directory, relative to the build's root, e.g. `"site"`.
 */
internal data class DependentModule(val gradlePath: String, val dir: String, val type: KobwebProjectType)

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
