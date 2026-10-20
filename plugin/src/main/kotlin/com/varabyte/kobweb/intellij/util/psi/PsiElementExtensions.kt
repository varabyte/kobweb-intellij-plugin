package com.varabyte.kobweb.intellij.util.psi

import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

/**
 * Given a PSI element that represents a piece of code inside a Kotlin dependency, fetch its containing klib.
 *
 * Returns null if not part of a klib, e.g. a file in a local module.
 */
internal val PsiElement.containingKlib: VirtualFile?
    get() {
        // Note: In-memory copies of files don't have a virtual file of their own
        return this.containingFile?.originalFile?.virtualFile?.let { virtualFile ->
            ProjectFileIndex.getInstance(this.project)
                .getOrderEntriesForFile(virtualFile)
                .asSequence()
                .filterIsInstance<LibraryOrderEntry>()
                .mapNotNull { it.library?.getFiles(OrderRootType.CLASSES)?.toList() }
                .flatten()
                .toSet() // Remove duplicates
                .singleOrNull { it.extension == "klib" }
        }
    }
