package com.varabyte.kobweb.intellij.wizards.module

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseStep
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

/** The kinds of Kobweb modules the wizard can create. */
private enum class KobwebModuleType(val text: String) {
    LIBRARY(UxGlobals.textBundle.message("wizard.kobweb.module.type.library")),
}

/**
 * The step of the Kobweb wizard that asks about the new module and then creates it.
 *
 * The module's name and location come from the [NewProjectWizardBaseStep] before it.
 */
class KobwebModuleStep(private val baseStep: NewProjectWizardBaseStep) : AbstractNewProjectWizardStep(baseStep) {
    private val typeProperty = propertyGraph.property(KobwebModuleType.LIBRARY)
    private val groupProperty = propertyGraph.lazyProperty(::suggestGroup)
    private val useSilkProperty = propertyGraph.property(true)
    private val useServerProperty = propertyGraph.property(false)
    private val useMarkdownProperty = propertyGraph.property(true)
    private val generateReadmeProperty = propertyGraph.property(true).bindBooleanStorage(GENERATE_README_PROPERTY_NAME)

    init {
        // Follows the module name and location until the user edits it
        groupProperty.dependsOn(baseStep.nameProperty, ::suggestGroup)
        groupProperty.dependsOn(baseStep.pathProperty, ::suggestGroup)
    }

    private val moduleDir: Path get() = Path(baseStep.path, baseStep.name)

    override fun setupUI(builder: Panel) {
        val bundle = UxGlobals.textBundle
        val project = context.project
        val gradleRoot = project?.findGradleRoot(moduleDir)
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

        with(builder) {
            row(bundle.message("wizard.kobweb.module.type")) {
                segmentedButton(KobwebModuleType.entries) { text = it.text }
                    .bind(typeProperty)
            }
            row(bundle.message("wizard.kobweb.module.group")) {
                textField()
                    .bindText(groupProperty)
                    .columns(COLUMNS_MEDIUM)
                    .validationOnInput { field -> validateGroup(field.text) }
                    .validationOnApply { field -> validateGroup(field.text) }
                    // The location field belongs to the previous step, so check here that it's still inside the build
                    .validationOnApply {
                        if (project.findGradleRoot(moduleDir) != null) null
                        else error(bundle.message("wizard.kobweb.module.error.not.gradle"))
                    }
            }
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
            row {
                checkBox(bundle.message("wizard.kobweb.module.generate.readme"))
                    .bindSelected(generateReadmeProperty)
                    .comment(bundle.message("wizard.kobweb.module.generate.readme.comment"))
            }
            if (gradleRoot.findFileByRelativePath("gradle/libs.versions.toml") == null) {
                row { comment(bundle.message("wizard.kobweb.module.catalog.created")) }
            }
        }
    }

    override fun setupProject(project: Project) {
        val gradleRoot = project.findGradleRoot(moduleDir) ?: return
        val relativeModuleDir = gradleRoot.toNioPath().relativize(moduleDir).invariantSeparatorsPathString
        val options = KobwebLibraryOptions(
            group = groupProperty.get(),
            useSilk = useSilkProperty.get(),
            useServer = useServerProperty.get(),
            useMarkdown = useMarkdownProperty.get(),
            generateReadme = generateReadmeProperty.get(),
        )

        val buildFile = WriteCommandAction.writeCommandAction(project)
            .withName(UxGlobals.textBundle.message("wizard.kobweb.module.command"))
            .compute<VirtualFile, Throwable> {
                KobwebModuleWriter(project, gradleRoot).writeLibrary(relativeModuleDir, options)
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
        /** Remembers across projects whether users want a README, as IntelliJ does for its "Add sample code" option. */
        const val GENERATE_README_PROPERTY_NAME = "kobweb.wizard.module.generateReadme"
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

private fun ValidationInfoBuilder.validateGroup(group: String): ValidationInfo? =
    if (group.isNotEmpty() && group.split('.').all { StringUtil.isJavaIdentifier(it) }) null
    else error(UxGlobals.textBundle.message("wizard.kobweb.module.error.invalid.group"))

/** The root directory of the Gradle build that [moduleDir] is inside of, or null if it isn't inside one. */
private fun Project.findGradleRoot(moduleDir: Path): VirtualFile? =
    GradleSettings.getInstance(this).linkedProjectsSettings
        .map { Path(it.externalProjectPath) }
        .filter { moduleDir.startsWith(it) && moduleDir != it }
        .maxByOrNull { it.nameCount }
        ?.let { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
