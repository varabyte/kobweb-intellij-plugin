package com.varabyte.kobweb.intellij.wizards.module

import com.intellij.ide.starters.local.generator.AssetsProcessor
import com.intellij.ide.wizard.AbstractNewProjectWizardMultiStepBase
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.runAfterOpened
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.ui.JBUI
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.notification.KobwebNotifier
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.services.icons.IconPreviewService
import com.varabyte.kobweb.intellij.services.icons.IconPreviewsListener
import com.varabyte.kobweb.intellij.services.icons.startLoading
import com.varabyte.kobweb.intellij.services.project.KobwebProjectScopeService
import com.varabyte.kobweb.intellij.services.versions.KobwebReleaseVersions
import com.varabyte.kobweb.intellij.services.versions.KobwebReleaseVersionsService
import com.varabyte.kobweb.intellij.util.idea.wizard.DeferredSetupStep
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconSet
import com.varabyte.kobweb.intellij.util.kotlin.isPackageSegment
import com.varabyte.kobweb.intellij.util.kotlin.toPackageSegment
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import org.jetbrains.plugins.gradle.service.project.wizard.AssetsGradle
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration.Companion.seconds

/**
 * The step of the Kobweb wizard that asks which kind of module to create, showing that kind's options below it.
 *
 * It also holds the options that all kinds share, so they keep their values when users switch between kinds. The
 * module's name and location come from the [NewProjectWizardBaseStep].
 *
 * When creating a new project, the base step names the project instead, and this step only offers an application,
 * which goes into the project's [NEW_PROJECT_MODULE_NAME] directory.
 */
class KobwebModuleStep(
    private val gitStep: DeferredSetupStep,
    private val baseStep: NewProjectWizardBaseStep,
) : AbstractNewProjectWizardMultiStepBase(gitStep) {
    override val label = UxGlobals.textBundle.message("wizard.kobweb.module.type")

    internal val moduleNameProperty get() = baseStep.nameProperty
    internal val groupProperty = propertyGraph.lazyProperty(::suggestGroup)
    internal val useSilkProperty = propertyGraph.property(true)
    private val iconSetProperty = propertyGraph.property<IconSetOption>(IconSetOption.None)
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
        // In a new project, the name field is the project's name, and the module is always [NEW_PROJECT_MODULE_NAME]
        if (!context.isCreatingNewProject) {
            // Follows the selected type until the user edits it, e.g. `site` for an application
            baseStep.nameProperty.dependsOn(stepProperty, ::suggestModuleName)
        }
        // Follows the module name and location until the user edits it
        groupProperty.dependsOn(baseStep.nameProperty, ::suggestGroup)
        groupProperty.dependsOn(baseStep.pathProperty, ::suggestGroup)
    }

    private val moduleDir: Path
        get() = if (context.isCreatingNewProject) Path(baseStep.path, baseStep.name, NEW_PROJECT_MODULE_NAME)
        else Path(baseStep.path, baseStep.name)

    override fun initSteps(): Map<String, NewProjectWizardStep> {
        val bundle = UxGlobals.textBundle
        if (context.isCreatingNewProject) {
            return linkedMapOf(bundle.message("wizard.kobweb.module.type.app") to KobwebAppStep(this))
        }
        return linkedMapOf(
            bundle.message("wizard.kobweb.module.type.app") to KobwebAppStep(this),
            bundle.message("wizard.kobweb.module.type.library") to KobwebLibraryStep(this),
            bundle.message("wizard.kobweb.module.type.worker") to KobwebWorkerStep(this),
        )
    }

    override fun setupUI(builder: Panel) {
        val bundle = UxGlobals.textBundle
        val gradleRoot = if (context.isCreatingNewProject) null else context.project?.findGradleRoot(moduleDir) ?: run {
            val message = bundle.message("wizard.kobweb.module.error.not.gradle")
            builder.row { label(message).validationOnApply { error(message) } }
            return
        }

        // Fetched while users fill in the form, so it's usually ready by the time they click Create
        KobwebReleaseVersionsService.instance.prefetch()

        super.setupUI(builder)
        if (gradleRoot != null && gradleRoot.findFileByRelativePath("gradle/libs.versions.toml") == null) {
            builder.row { comment(bundle.message("wizard.kobweb.module.catalog.created")) }
        }
    }

    /** Leaves out the type switcher when there's only one type to pick, e.g. when creating a new project. */
    override fun setupSwitcherUi(builder: Panel) {
        if (steps.size > 1) super.setupSwitcherUi(builder)
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
                    if (context.isCreatingNewProject || context.project?.findGradleRoot(moduleDir) != null) null
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
        // Silk's icon sets are built on Silk, so they're only offered with it
        indent {
            row(bundle.message("wizard.kobweb.module.icon.set")) {
                IconSetOption.IconSet.entries.forEach {
                    // Prefetch, so hopefully icons will be ready before the icon dropdown is opened.
                    IconPreviewService.instance.startLoading(it.iconSet, iconSetVersion = null)
                }
                comboBox(IconSetOption.entries, IconSetOption.createRenderer())
                    .bindItem(iconSetProperty)
                    .applyToComponent {
                        val listener = IconPreviewsListener {
                            ApplicationManager.getApplication()
                                .invokeLater({ repaint() }, ModalityState.stateForComponent(this))
                        }
                        ApplicationManager.getApplication().messageBus.connect(context.disposable)
                            .subscribe(IconPreviewService.ICON_SET_LOADED, listener)
                    }
            }.enabledIf(useSilkProperty)
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

    /** The icon set chosen in [featureRows], or null if none was chosen or Silk isn't used. */
    internal val selectedIconSet: SilkIconSet?
        get() = if (useSilkProperty.get()) (iconSetProperty.get() as? IconSetOption.IconSet)?.iconSet else null

    /** The modules chosen in [dependentsRow]. */
    internal val selectedDependents: List<DependentModule>
        get() = dependentProperties.filterValues { it.get() }.keys.toList()

    /**
     * Writes the new module with [write], which gets a path relative to the build's root (e.g. `"site"`) and returns
     * the module's build script, and adds it as a dependency of [dependents]. Then syncs Gradle and opens the build
     * script.
     *
     * When creating a new project, this writes the project's root files too (see [writeNewProject]).
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
        if (context.isCreatingNewProject) {
            writeNewProject(project, write)
            return
        }

        val gradleRoot = project.findGradleRoot(moduleDir) ?: return
        val relativeModuleDir = gradleRoot.toNioPath().relativize(moduleDir).invariantSeparatorsPathString
        val writer = KobwebModuleWriter(project, gradleRoot, versionOverrides = awaitLatestKobwebVersions(project))

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

    /**
     * Writes a new project with the application [write] writes. Its files are only written once the project opens,
     * since the Gradle DSL can't edit files before then. Git's setup waits for them, so the new repository includes
     * them, and then the project is linked to Gradle.
     */
    private fun writeNewProject(project: Project, write: KobwebModuleWriter.(moduleDir: String) -> VirtualFile) {
        val rootPath = Path(baseStep.path, baseStep.name)
        val projectName = baseStep.name
        @Suppress("UnstableApiUsage") // `runAfterOpened` is what IJ wizards themselves use; should be fine. If this
        // ever breaks, we can write our own solution instead.
        runAfterOpened(project) { openedProject ->
            openedProject.service<KobwebProjectScopeService>().launch("Kobweb new project setup") {
                val versions = KobwebReleaseVersionsService.instance.awaitLatestVersions(LATEST_VERSIONS_TIMEOUT)
                val rootDir = withContext(Dispatchers.IO) {
                    createGradleWrapper(rootPath, versions?.gradleVersion ?: NEW_PROJECT_GRADLE_VERSION)
                }
                withContext(Dispatchers.EDT) {
                    val writer = KobwebModuleWriter(openedProject, rootDir, versions?.toVersionOverrides().orEmpty())
                    val buildFile = WriteCommandAction.writeCommandAction(openedProject)
                        .withName(UxGlobals.textBundle.message("wizard.kobweb.module.command"))
                        .compute<VirtualFile, Throwable> {
                            writer.writeProjectRoot(projectName)
                            writer.write(NEW_PROJECT_MODULE_NAME)
                        }
                    OpenFileDescriptor(openedProject, buildFile).navigate(/* requestFocus = */ true)
                }
                gitStep.setupProjectLater(openedProject)
                linkAndSyncGradleProject(openedProject, rootDir)
            }
        }
    }

    /**
     * Writes a Gradle wrapper for [gradleVersion] into the new project's [rootPath], returning the root directory. The
     * wrapper needs nothing specific to Kobweb, so it comes from IntelliJ's own wizards.
     */
    private fun createGradleWrapper(rootPath: Path, gradleVersion: String): VirtualFile {
        val wrapperAssets = AssetsGradle.getGradleWrapperAssets(GradleVersion.version(gradleVersion))
        // `AssetsProcessor` is what IJ wizards themselves use; should be fine. If this ever breaks, we can write our
        // own solution instead.
        @Suppress("UnstableApiUsage")
        AssetsProcessor.getInstance().generateSources(rootPath, wrapperAssets, emptyMap())
        val rootDir = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(rootPath))
        VfsUtil.markDirtyAndRefresh(/* async = */ false, /* recursive = */ true, /* reloadChildren = */ true, rootDir)
        return rootDir
    }

    /**
     * The versions the latest Kobweb release uses, waiting briefly for them if they're still being fetched. If they
     * can't be fetched (e.g. when offline), returns no versions, so the module uses the ones bundled with the plugin.
     */
    private fun awaitLatestKobwebVersions(project: Project): Map<String, String> {
        val service = KobwebReleaseVersionsService.instance
        service.latestVersionsIfFetched()?.let { return it.toVersionOverrides() }
        val title = UxGlobals.textBundle.message("wizard.kobweb.module.fetching.versions")
        return runWithModalProgressBlocking(project, title) {
            service.awaitLatestVersions(LATEST_VERSIONS_TIMEOUT)
        }?.toVersionOverrides().orEmpty()
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
     *
     * In a new project, the build's group is [NEW_PROJECT_GROUP] plus the project's name, e.g.
     * `org.example.myproject.site`.
     */
    private fun suggestGroup(): String {
        if (context.isCreatingNewProject) {
            return listOfNotNull(NEW_PROJECT_GROUP, baseStep.name.toPackageSegment(), NEW_PROJECT_MODULE_NAME)
                .joinToString(".")
        }
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
        return listOfNotNull(buildGroup, baseStep.name.toPackageSegment()).joinToString(".")
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

    internal companion object {
        /** The directory of a new project's application module, as `kobweb create app` names it. */
        const val NEW_PROJECT_MODULE_NAME = "site"

        /** The group that a new project's groups start with, followed by the project's name. */
        private const val NEW_PROJECT_GROUP = "org.example"

        /**
         * The Gradle version of a new project's wrapper if the latest Kobweb release's can't be fetched: the one
         * Kobweb's latest release (at the time this plugin was released) was built with.
         */
        private const val NEW_PROJECT_GRADLE_VERSION = "9.7.0"

        /** How long Create waits for the latest Kobweb versions, if they're still being fetched. */
        private val LATEST_VERSIONS_TIMEOUT = 2.seconds

        /** The popup of dependents scrolls past this many. */
        private const val MAX_VISIBLE_DEPENDENTS = 10

        /** The dependents row names this many of the chosen dependents, then counts the rest. */
        private const val MAX_SUMMARIZED_DEPENDENTS = 2

        // These options are remembered across projects, as IntelliJ does for its own "Add sample code" option
        private const val GENERATE_README_PROPERTY_NAME = "kobweb.wizard.module.generateReadme"
        private const val ADD_SAMPLE_CODE_PROPERTY_NAME = "kobweb.wizard.module.addSampleCode"
    }
}

/**
 * An application or library in the build that can depend on the new module.
 *
 * @property gradlePath e.g. `:site`
 * @property dir The module's directory, relative to the build's root, e.g. `"site"`.
 */
internal data class DependentModule(val gradlePath: String, val dir: String, val type: KobwebProjectType)

/**
 * These versions as overrides for the template catalog, which has a `[versions]` entry for each of the
 * template's libraries and plugins (e.g. `kotlin`).
 *
 * The Kobweb version comes from the release tag rather than from Kobweb's own catalog, whose key for it may not
 * match the template's. Other keys are matched by name, so any the template doesn't share keep its bundled version.
 */
private fun KobwebReleaseVersions.toVersionOverrides(): Map<String, String> =
    catalogVersions + ("kobweb" to kobwebVersion)

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
    if (group.isNotEmpty() && group.split('.').all { it.isPackageSegment() }) null
    else error(UxGlobals.textBundle.message("wizard.kobweb.module.error.invalid.group", exampleGroup))

/** The root directory of the Gradle build that [moduleDir] is inside of, or null if it isn't inside one. */
private fun Project.findGradleRoot(moduleDir: Path): VirtualFile? =
    GradleSettings.getInstance(this).linkedProjectsSettings
        .map { Path(it.externalProjectPath) }
        .filter { moduleDir.startsWith(it) && moduleDir != it }
        .maxByOrNull { it.nameCount }
        ?.let { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
