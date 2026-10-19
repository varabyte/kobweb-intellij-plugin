package com.varabyte.kobweb.intellij.rename.worker

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import com.varabyte.kobweb.intellij.resolve.worker.findSourceWorkerFactoryClass
import com.varabyte.kobweb.intellij.resolve.worker.findSynthesizedWorkerClass
import com.varabyte.kobweb.intellij.util.kobweb.KobwebProjectTypes
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.worker.WorkerFactoryClass
import com.varabyte.kobweb.intellij.util.kobweb.worker.asWorkerFactoryClass
import com.varabyte.kobweb.intellij.util.kobweb.worker.computeWorkerClassId
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor

/** Marks the synthesized worker class added by [WorkerFactoryRenameProcessor], for [SynthesizedWorkerClassRenameProcessor]. */
private val IS_SYNTHESIZED_WORKER_CLASS_KEY = Key<Boolean>("kobweb.worker.isSynthesizedWorkerClass")

/**
 * When renaming a `WorkerFactory` implementation, also rename the `Worker` class generated from it.
 *
 * Kobweb derives the worker class's name from the factory's name (e.g. `CalculatePiWorkerFactory` generates
 * `CalculatePiWorker`), so renaming the factory renames the worker class too. Without this, every reference to the
 * worker class would break.
 *
 * NOTE: IntelliJ consults *every* applicable processor in [prepareRenaming], which is the
 * only part we need. (We don't use an `AutomaticRenamerFactory` for this, as that always asks the user to confirm.)
 */
class WorkerFactoryRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean {
        if (!element.isInWritableKobwebProject(KobwebProjectTypes.WorkerOnly)) return false
        return (element as? KtClassOrObject)?.asWorkerFactoryClass() != null
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val factoryClass = (element as? KtClassOrObject)?.asWorkerFactoryClass() ?: return

        val fqcnOverride = element.findKobwebProject()?.worker?.fqcn
        val oldWorkerClassId = factoryClass.computeWorkerClassId(fqcnOverride) ?: return
        val newWorkerClassId = WorkerFactoryClass.computeWorkerClassId(
            factoryClass.factoryFqName.parent().child(Name.identifier(newName)),
            factoryClass.packageFqName,
            fqcnOverride,
        ) ?: return

        // e.g. if `fqcn` sets the worker's class name explicitly, renaming the factory doesn't affect it
        if (oldWorkerClassId == newWorkerClassId) return

        // This method is called on the UI thread, where Kotlin analysis isn't allowed, so do that part in the background
        val workerClass = ActionUtil.underModalProgress(
            element.project,
            UxGlobals.textBundle.message("rename.worker.finding.worker.class"),
        ) {
            analyze(factoryClass.ktClass) { factoryClass.findSynthesizedWorkerClass(oldWorkerClassId) }
        } ?: return
        workerClass.putUserData(IS_SYNTHESIZED_WORKER_CLASS_KEY, true)
        allRenames[workerClass] = newWorkerClassId.shortClassName.asString()
    }
}

/**
 * Handles renames of the synthesized worker class.
 *
 * When the user renames the worker class directly, we refuse, since its name is derived from the factory (or set by
 * `fqcn` in the build script). Otherwise, the worker's usages would be renamed while the class itself would not be.
 *
 * When [WorkerFactoryRenameProcessor] adds the worker class to a factory rename, we just update every reference to it.
 * The synthesized class itself doesn't need renaming, since it's regenerated from the (renamed) factory automatically.
 * We also can't let the Kotlin plugin's own rename processor handle it, since it fails to analyze a class that only
 * exists in memory.
 */
class SynthesizedWorkerClassRenameProcessor : RenamePsiElementProcessor() {
    // Renaming a class from a constructor call (e.g. `CalculatePiWorker { ... }`) targets its constructor instead
    private fun PsiElement.asClassOrObject(): KtClassOrObject? {
        return this as? KtClassOrObject ?: (this as? KtConstructor<*>)?.getContainingClassOrObject()
    }

    override fun canProcessElement(element: PsiElement): Boolean {
        if (element.getUserData(IS_SYNTHESIZED_WORKER_CLASS_KEY) == true) return true
        return element.asClassOrObject()?.findSourceWorkerFactoryClass() != null
    }

    // Only called for the element the user asked to rename, not for elements added by `prepareRenaming`
    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement? {
        val workerClass = element.asClassOrObject() ?: return element
        val factoryClass = workerClass.findSourceWorkerFactoryClass() ?: return element

        val messageKey = if (factoryClass.ktClass.findKobwebProject()?.worker?.fqcn != null) {
            "rename.worker.cannot.rename.worker.class.fqcn"
        } else {
            "rename.worker.cannot.rename.worker.class"
        }
        CommonRefactoringUtil.showErrorHint(
            element.project,
            editor,
            UxGlobals.textBundle.message(messageKey, workerClass.name.orEmpty(), factoryClass.ktClass.name.orEmpty()),
            RefactoringBundle.message("rename.title"),
            null,
        )
        return null
    }

    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<out UsageInfo>,
        listener: RefactoringElementListener?,
    ) {
        usages.forEach { usage -> usage.reference?.handleElementRename(newName) }
    }
}
