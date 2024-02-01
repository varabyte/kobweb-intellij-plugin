package com.varabyte.kobweb.intellij.util.kobweb

import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import org.jetbrains.kotlin.psi.KtFile

/**
 * A collection of useful sets of [KobwebProjectType]s.
 *
 * These can be useful to pass into the [isInReadOnlyKobwebContext] and [isInWritableKobwebContext] extension methods.
 */
object KobwebProjectTypes {
    /**
     * The set of all Kobweb project types.
     */
    val All = KobwebProjectType.entries.toSet()

    /**
     * The set of core Kobweb project types that affect the frontend DOM / backend API routes.
     */
    val Framework = setOf(KobwebProjectType.Application, KobwebProjectType.Library)

    val WorkerOnly = setOf(KobwebProjectType.Worker)
}

private fun PsiElement.isInKobwebSource(): Boolean {
    return (this.containingFile as? KtFile)?.packageFqName?.asString()?.startsWith("com.varabyte.kobweb") ?: false
}

private fun PsiElement.isInKobwebContext(test: (KobwebProject) -> Boolean): Boolean {
    this.findKobwebProject()?.let { return test(it) } ?: return false
}

/**
 * Useful test to see if a read-only Kobweb Plugin action (like an inspection) should run here.
 *
 * @param limitTo The [KobwebProject] types to limit this action to. By default, restricted to presentation types (that
 *   is, the parts of Kobweb that interact with the DOM). This default was chosen because this is by far the most
 *   common case, the kind of code that most people associate with Kobweb.
 *
 * @param excludeKobwebSource If true, then disable this action if the user is navigating around Kobweb source code.
 *   By default, this is false, so unless this is explicitly set, this extension point will be active inside Kobweb
 *   source.
 */
fun PsiElement.isInReadOnlyKobwebContext(limitTo: Set<KobwebProjectType> = KobwebProjectTypes.Framework, excludeKobwebSource: Boolean = true): Boolean {
    return isInKobwebContext { it.type in limitTo } || (!excludeKobwebSource && isInKobwebSource())
}

/**
 * Useful test to see if a writing Kobweb Plugin action (like a refactor) should be allowed to run here.
 *
 * @param limitTo See the docs for [isInReadOnlyKobwebContext] for more info.
 */
fun PsiElement.isInWritableKobwebContext(limitTo: Set<KobwebProjectType> = KobwebProjectTypes.Framework): Boolean {
    return isInKobwebContext { it.type in limitTo && it.source is KobwebProject.Source.Local }
}
