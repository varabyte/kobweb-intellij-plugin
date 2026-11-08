package com.varabyte.kobweb.intellij.rename.module

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.notification.NotificationType
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiDirectoryProcessor
import com.intellij.usageView.UsageInfo
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.notification.KobwebNotifier
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.conf.KOBWEB_CONF_PATH
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.text.capitalized
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.plainContent
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

private val SETTINGS_FILE_NAMES = listOf("settings.gradle.kts", "settings.gradle")
private const val BUILD_FILE_NAME = "build.gradle.kts"

/** The names a module's build script can have, Kotlin first. */
private val BUILD_FILE_NAMES = listOf(BUILD_FILE_NAME, "build.gradle")

/** The Gradle function for depending on another module of the build, e.g. `project(":library")`. */
private const val PROJECT_FUNCTION = "project"

/** The root of Gradle's type-safe project accessors, e.g. `projects.library`. */
private const val PROJECTS_ACCESSOR = "projects"

/** The Kobweb configuration function, and its name parameter, for each type of module that has one. */
private val NAME_PARAMS = mapOf(
    KobwebProjectType.Application to ("configAsKobwebApplication" to "moduleName"),
    KobwebProjectType.Worker to ("configAsKobwebWorker" to "workerName"),
)

/** The `.kobweb/conf.yaml` keys whose values point at files named after the application. */
private val APPLICATION_FILE_KEYS = listOf("script", "api")

/**
 * Renames a Kobweb module's folder along with the names that refer to it, e.g. `site` to `website`.
 *
 * Besides the folder, this updates the module's `include(...)` in the settings script. If the name passed to
 * `configAsKobwebApplication(...)` or `configAsKobwebWorker(...)` is the folder's name (as in new Kobweb projects), it's
 * renamed too, along with the `.kobweb/conf.yaml` paths to the application's files. Gradle is synced afterwards.
 *
 * Kobweb modules are found through the project model from the last Gradle sync. Modules whose folder is set with
 * `projectDir` in the settings script are left to IntelliJ's plain directory rename (which updates the `file(...)` path
 * itself), since their folder's name and their Gradle path are unrelated.
 */
class KobwebModuleRenameProcessor : RenamePsiDirectoryProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean {
        val dir = element as? PsiDirectory ?: return false
        return dir.isWritable && dir.findKobwebModule() != null
    }

    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<out UsageInfo>,
        listener: RefactoringElementListener?,
    ) {
        val dir = element as PsiDirectory
        val module = dir.findKobwebModule()
        val oldName = dir.name
        // Checked before the folder is renamed, since IntelliJ may update the name along with it, treating it as a path
        val isNamedAfterFolder = module != null && dir.findBuildScript()?.findNameArgument(module.type)?.plainContent == oldName
        super.renameElement(element, newName, usages, listener)
        if (module == null) return

        val settingsFile = module.rootDir.findSettingsFile()
        if (settingsFile != null) {
            val newGradlePath = module.gradlePath.substringBeforeLast(':') + ":$newName"
            val settings = GradleSettingsModel.get(settingsFile, dir.project)
            val unchangedBuildFiles = settings.renameDependencies(dir.project, module.rootDir, module.gradlePath, newGradlePath)
            settings.replaceModulePath(module.gradlePath, newGradlePath)
            settings.applyChanges()
            if (unchangedBuildFiles.isNotEmpty()) {
                notifyAccessorsNotRenamed(dir.project, unchangedBuildFiles, module.gradlePath, newGradlePath)
            }
        }

        if (isNamedAfterFolder) {
            dir.findBuildScript()?.findNameArgument(module.type)?.let { name ->
                if (name.plainContent != newName) name.replace(KtPsiFactory(dir.project).createStringTemplate(newName))
            }
            if (module.type == KobwebProjectType.Application) {
                dir.virtualFile.findFileByRelativePath(KOBWEB_CONF_PATH)
                    ?.let { PsiManager.getInstance(dir.project).findFile(it) }
                    ?.renameApplicationFiles(oldName, newName)
            }
        }
    }

    override fun getPostRenameCallback(
        element: PsiElement,
        newName: String,
        elementListener: RefactoringElementListener,
    ): Runnable? {
        val rootPath = (element as? PsiDirectory)?.findKobwebModule()?.rootDir?.path ?: return null
        // Only builds IntelliJ imported from Gradle can be synced
        if (GradleSettings.getInstance(element.project).getLinkedProjectSettings(rootPath) == null) return null
        return Runnable {
            ExternalSystemUtil.refreshProject(rootPath, ImportSpecBuilder(element.project, GradleConstants.SYSTEM_ID))
        }
    }

    /**
     * The local Kobweb module whose Gradle project is in this folder, if its Gradle path ends with the folder's name.
     */
    private fun PsiDirectory.findKobwebModule(): KobwebModule? {
        val path = virtualFile.path
        // Source set modules (e.g. "site.jsMain") point at the same folder as their Gradle project's module, and all of
        // them lead to the same Kobweb project and Gradle path
        val module = ModuleManager.getInstance(project).modules
            .firstOrNull { ExternalSystemApiUtil.getExternalProjectPath(it) == path } ?: return null
        val kobwebProject = module.findKobwebProject()?.takeIf { it.source is KobwebProject.Source.Local } ?: return null
        val gradlePath = GradleProjectResolverUtil.getGradleIdentityPathOrNull(module) ?: return null
        if (gradlePath.substringAfterLast(':') != name) return null
        val rootPath = ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return null
        val rootDir = generateSequence(virtualFile.parent) { it.parent }.firstOrNull { it.path == rootPath } ?: return null
        return KobwebModule(kobwebProject.type, gradlePath, rootDir)
    }

    /**
     * Changes every `project(oldPath)` dependency in the build scripts of this build's modules to [newPath].
     *
     * @return the build scripts that still refer to the module through a type-safe project accessor (e.g.
     *   `projects.library`), which aren't renamed.
     */
    private fun GradleSettingsModel.renameDependencies(
        project: Project,
        rootDir: VirtualFile,
        oldPath: String,
        newPath: String,
    ): List<VirtualFile> {
        val unchangedBuildFiles = mutableListOf<VirtualFile>()
        for (path in modulePaths()) {
            if (path == oldPath) continue
            val moduleDir = moduleDirectory(path)?.invariantSeparatorsPath?.removePrefix(rootDir.path)?.removePrefix("/")
                ?: continue
            val buildFile = BUILD_FILE_NAMES.firstNotNullOfOrNull { rootDir.findFileByRelativePath("$moduleDir/$it") }
                ?: continue
            val kotlinScript = PsiManager.getInstance(project).findFile(buildFile) as? KtFile
            if (kotlinScript != null) {
                kotlinScript.renameProjectCalls(oldPath, newPath)
                if (kotlinScript.hasProjectAccessor(oldPath)) unchangedBuildFiles.add(buildFile)
            } else {
                ProjectBuildModel.get(project).getModuleBuildModel(buildFile).renameModuleDependencies(oldPath, newPath)
            }
        }
        return unchangedBuildFiles
    }

    /**
     * Changes every `project(":oldPath")` call in this Kotlin build script to [newPath].
     *
     * The Gradle DSL isn't used for Kotlin scripts, since it misses dependencies declared in some common ways, e.g. in
     * `val jsMain by getting { dependencies { ... } }`.
     */
    private fun KtFile.renameProjectCalls(oldPath: String, newPath: String) {
        for (call in collectDescendantsOfType<KtCallExpression>()) {
            if (call.calleeExpression?.text != PROJECT_FUNCTION) continue
            val path = call.valueArguments.singleOrNull()?.getArgumentExpression() as? KtStringTemplateExpression ?: continue
            if (path.hasInterpolation() || path.plainContent != oldPath) continue
            ElementManipulators.handleContentChange(path, newPath)
        }
    }

    /**
     * Whether this Kotlin build script refers to the module at [gradlePath] through a type-safe project accessor, e.g.
     * `projects.libs.myLib` for `:libs:my-lib`.
     */
    private fun KtFile.hasProjectAccessor(gradlePath: String): Boolean {
        val accessor = gradlePath.toProjectAccessor()
        return collectDescendantsOfType<KtDotQualifiedExpression>()
            .any { expression -> expression.text.filterNot { it.isWhitespace() } == accessor }
    }

    /**
     * Changes every `project(oldPath)` dependency in this (Groovy) build script to [newPath], both in its top-level
     * `dependencies` block and in its Kotlin source sets' ones.
     */
    private fun GradleBuildModel.renameModuleDependencies(oldPath: String, newPath: String) {
        val dependencyBlocks = listOf(dependencies()) + kotlin().sourceSets().map { it.dependencies() }
        for (dependency in dependencyBlocks.flatMap { it.modules() }) {
            if (dependency.path().forceString() != oldPath) continue
            // Only the path's string is replaced, since the DSL can rewrite surrounding blocks when it saves a change
            // (e.g. `jsMain.dependencies { ... }` into `create("jsMain") { dependencies { ... } }`)
            val pathString = dependency.path().expressionPsiElement ?: continue
            ElementManipulators.handleContentChange(pathString, newPath)
        }
    }

    /** Warns that [buildFiles] still refer to the module at [oldPath] through a type-safe project accessor. */
    private fun notifyAccessorsNotRenamed(project: Project, buildFiles: List<VirtualFile>, oldPath: String, newPath: String) {
        val bundle = UxGlobals.textBundle
        val builder = KobwebNotifier.Builder(
            bundle.message(
                "rename.module.accessors.not.renamed",
                oldPath.toProjectAccessor(),
                newPath.toProjectAccessor(),
            )
        ).type(NotificationType.WARNING)
        for (buildFile in buildFiles) {
            builder.addAction(bundle.message("rename.module.accessors.not.renamed.open", buildFile.parent.name, buildFile.name)) {
                OpenFileDescriptor(project, buildFile).navigate(/* requestFocus = */ true)
            }
        }
        builder.notify(project)
    }

    /**
     * The type-safe project accessor Gradle generates for the module at this Gradle path, e.g. `projects.libs.myLib`
     * for `:libs:my-lib`. Gradle turns kebab-case and snake_case names into camelCase ones.
     */
    private fun String.toProjectAccessor(): String {
        fun String.toAccessorName(): String {
            val parts = split('-', '_')
            return parts.first() + parts.drop(1).joinToString("") { it.capitalized() }
        }
        return (listOf(PROJECTS_ACCESSOR) + removePrefix(":").split(':').map { it.toAccessorName() }).joinToString(".")
    }

    private fun VirtualFile.findSettingsFile(): VirtualFile? = SETTINGS_FILE_NAMES.firstNotNullOfOrNull { findChild(it) }

    private fun PsiDirectory.findBuildScript(): KtFile? = findFile(BUILD_FILE_NAME) as? KtFile

    /**
     * The name passed to `configAsKobwebApplication(...)` (for an application) or `configAsKobwebWorker(...)` (for a
     * worker) in this build script, if it's a plain string.
     */
    private fun KtFile.findNameArgument(type: KobwebProjectType): KtStringTemplateExpression? {
        val (function, nameParam) = NAME_PARAMS[type] ?: return null
        val call = collectDescendantsOfType<KtCallExpression>()
            .firstOrNull { it.calleeExpression?.text == function } ?: return null
        // The name is the first parameter of both functions
        val argument = call.valueArguments.firstOrNull { it.getArgumentName()?.asName?.asString() == nameParam }
            ?: call.valueArguments.firstOrNull()?.takeUnless { it.isNamed() }
        return (argument?.getArgumentExpression() as? KtStringTemplateExpression)?.takeUnless { it.hasInterpolation() }
    }

    /**
     * Renames the application's script and server files (e.g. `build/libs/site.jar`) in this `.kobweb/conf.yaml` file
     * from [oldName] to [newName].
     */
    private fun PsiFile.renameApplicationFiles(oldName: String, newName: String) {
        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(this) ?: return
        for (line in (0 until document.lineCount).reversed()) {
            val start = document.getLineStartOffset(line)
            val end = document.getLineEndOffset(line)
            val text = document.getText(TextRange(start, end))
            if (APPLICATION_FILE_KEYS.none { text.trimStart().startsWith("$it:") }) continue
            val newText = text
                .replace("/$oldName.js", "/$newName.js")
                .replace("/$oldName.jar", "/$newName.jar")
            if (newText != text) document.replaceString(start, end, newText)
        }
        documentManager.commitDocument(document)
    }

    /**
     * @property gradlePath The module's Gradle path, e.g. `:libs:mylib`.
     * @property rootDir The root folder of the module's Gradle build, which holds its settings script.
     */
    private class KobwebModule(val type: KobwebProjectType, val gradlePath: String, val rootDir: VirtualFile)
}
