package com.varabyte.kobweb.intellij.references.layout

import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.isKobwebPluginEnabled
import com.varabyte.kobweb.intellij.util.kobweb.layout.isLayoutFunction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Returns the layouts declared in this directory's Kotlin files, including those in its subdirectories.
 */
private fun PsiDirectory.findLayouts(): List<KtNamedFunction> {
    return files.filterIsInstance<KtFile>().flatMap { it.findLayouts() } + subdirectories.flatMap { it.findLayouts() }
}

/**
 * Returns the layouts declared in this file.
 */
private fun KtFile.findLayouts(): List<KtNamedFunction> {
    // Layouts are always top-level functions
    return declarations.filterIsInstance<KtNamedFunction>().filter { it.isLayoutFunction() }
}

/**
 * Updates `@Layout` paths when the layout they point at is moved (e.g. to another package).
 *
 * This handles moving the layout itself (e.g. with F6), as well as moving the file or directory it's declared in (e.g.
 * by dragging it into another package).
 *
 * Kotlin's Move refactoring doesn't update these paths itself, as it only knows how to update references it finds
 * through Java's view of Kotlin code, which Kotlin/JS code (like Kobweb sites) doesn't have. It does, however, notify
 * listeners about what it moves.
 */
class LayoutMoveListenerProvider : RefactoringElementListenerProvider {
    override fun getListener(element: PsiElement): RefactoringElementListener? {
        // This is called for every element any refactoring touches, so bail out quickly (using only the PSI) for
        // anything that can't be or contain a layout
        val layouts = when (element) {
            is KtNamedFunction -> listOf(element).filter { it.isLayoutFunction() }
            is KtFile -> element.findLayouts()
            // Avoid walking directories in projects that don't use Kobweb at all
            is PsiDirectory -> if (element.project.isKobwebPluginEnabled) element.findLayouts() else emptyList()
            else -> emptyList()
        }.filter { it.isInWritableKobwebProject() }
        if (layouts.isEmpty()) return null

        // Find the paths now, as after the move they'll no longer resolve to their layouts
        val pathsByLayout = layouts
            .associate { layout ->
                val paths = ReferencesSearch.search(layout).findAll()
                    .filterIsInstance<LayoutPathReference>()
                    .map { reference -> SmartPointerManager.createPointer(reference.element) }
                SmartPointerManager.createPointer(layout) to paths
            }
            .filterValues { paths -> paths.isNotEmpty() }
        if (pathsByLayout.isEmpty()) return null

        return object : RefactoringElementListener {
            override fun elementMoved(newElement: PsiElement) {
                // Listeners are notified after the refactoring's write action ends, so start our own
                WriteAction.run<Throwable> {
                    pathsByLayout.forEach { (layoutPointer, paths) ->
                        // Moving a layout creates a new function in its new location, which is `newElement`, while
                        // moving its file (or directory) keeps the same function, just in a new package
                        val layout = newElement as? KtNamedFunction ?: layoutPointer.element ?: return@forEach
                        for (path in paths) {
                            val reference =
                                path.element?.references?.filterIsInstance<LayoutPathReference>()?.firstOrNull()
                            reference?.bindToElement(layout)
                        }
                    }
                }
            }

            // Renaming a layout already updates its paths, as each path's reference covers just the layout's name
            override fun elementRenamed(newElement: PsiElement) = Unit
        }
    }
}
