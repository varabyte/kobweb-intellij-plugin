package com.varabyte.kobweb.intellij.util.kobweb.icons

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.plugins.gradle.util.GradleConstants

/** The functions that add an icon set inside Kobweb's `icons { ... }` block, named after each set's ID. */
private val ICON_SET_FUNCTIONS = setOf("fa", "ms", "lucide", "mdi")

/** The blocks inside `kobweb { ... }` that can contain an `icons { ... }` block. */
private val ICONS_PARENT_BLOCKS = setOf("app", "library")

/** Where Kobweb's `kobwebFetchIcons` task writes icon data, relative to the module's folder. */
private const val ICONS_STORE_PATH = "kobweb-store/icons"

private val KtCallExpression.calleeName get() = calleeExpression?.text

/** The call whose lambda (or arguments) this call is inside of, e.g. `icons` for the `fa()` in `icons { fa() }`. */
private fun KtCallExpression.enclosingCall() = parentOfType<KtCallExpression>(withSelf = false)

/**
 * Whether this is a call to `icons { ... }` inside `kobweb { app { ... } }` or `kobweb { library { ... } }`.
 *
 * Calls are matched by name, as resolving them in a build script is slow and only works once Gradle has been synced.
 */
fun KtCallExpression.isIconsBlock(): Boolean {
    if (!containingFile.name.endsWith(".gradle.kts")) return false
    if (calleeName != "icons") return false
    val parentBlock = enclosingCall()?.takeIf { it.calleeName in ICONS_PARENT_BLOCKS } ?: return false
    return parentBlock.enclosingCall()?.calleeName == "kobweb"
}

/** The ID of the icon set (e.g. "fa") this call adds, if it's a call like `fa()` inside an [isIconsBlock] call. */
fun KtCallExpression.findIconSetId(): String? {
    val id = calleeName?.takeIf { it in ICON_SET_FUNCTIONS } ?: return null
    return id.takeIf { enclosingCall()?.isIconsBlock() == true }
}

/** The lock file `kobwebFetchIcons` writes for the icon set with this [id], next to this build script. */
fun PsiFile.findIconsLockFile(id: String): VirtualFile? =
    virtualFile?.parent?.findFileByRelativePath("$ICONS_STORE_PATH/$id.lock")

/**
 * Fetch the icon sets declared in this build script, then generate their bindings, by running Kobweb's
 * `kobwebFetchIcons` and `kobwebGenerateIcons` tasks.
 *
 * The bindings are generated right away so they can be used without waiting for the next build. Afterwards, the
 * module's folder is refreshed, so the new files show up (and inspections notice them) immediately.
 */
fun PsiFile.fetchIcons() {
    val moduleDir = virtualFile?.parent ?: return
    val settings = ExternalSystemTaskExecutionSettings().apply {
        externalProjectPath = moduleDir.path
        taskNames = listOf("kobwebFetchIcons", "kobwebGenerateIcons")
        externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }
    ExternalSystemUtil.runTask(
        TaskExecutionSpec.create()
            .withProject(project)
            .withSystemId(GradleConstants.SYSTEM_ID)
            .withSettings(settings)
            .withProgressExecutionMode(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
            .withCallback(object : TaskCallback {
                override fun onSuccess() {
                    val dirtyFiles = VfsUtil.markDirty(/* recursive = */ true, /* reloadChildren = */ true, moduleDir)
                    LocalFileSystem.getInstance().refreshFiles(dirtyFiles, /* async = */ true, /* recursive = */ true) {
                        if (isValid) DaemonCodeAnalyzer.getInstance(project).restart(this@fetchIcons)
                    }
                }

                override fun onFailure() = Unit
            })
            .build()
    )
}
