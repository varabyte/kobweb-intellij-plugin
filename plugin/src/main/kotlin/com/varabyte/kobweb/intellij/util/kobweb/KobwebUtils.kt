package com.varabyte.kobweb.intellij.util.kobweb

import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import org.jetbrains.kotlin.psi.KtFile

/**
 * A collection of useful sets of [KobwebProjectType]s.
 *
 * These can be useful to pass into the [isInReadableKobwebProject] and [isInWritableKobwebProject] extension methods.
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

/**
 * Returns true if this is code inside the Kobweb framework itself.
 *
 * The user can easily end up in here if they navigate into it from their own code, e.g. to see how something is
 * implemented or to look around at the docs or other API methods.
 *
 * Not every extension point we implement for the Kobweb plugin should be enabled for the framework itself, but some
 * should be, so this method is provided for the extension point implementor to decide how broad it should apply.
 */
fun PsiElement.isInKobwebSource(): Boolean {
    return (this.containingFile as? KtFile)?.packageFqName?.asString()?.startsWith("com.varabyte.kobweb") ?: false
}

private fun PsiElement.isInKobwebProject(test: (KobwebProject) -> Boolean): Boolean {
    this.findKobwebProject()?.let { return test(it) } ?: return false
}

/**
 * Useful test to see if a read-only Kobweb Plugin action (like an inspection) should run here.
 *
 * @param limitTo The [KobwebProject] types to limit this action to. By default, restricted to presentation types (that
 *   is, the parts of Kobweb that interact with the DOM). This default was chosen because this is by far the most
 *   common case, the kind of code that most people associate with Kobweb.
 */
fun PsiElement.isInReadableKobwebProject(limitTo: Set<KobwebProjectType> = KobwebProjectTypes.Framework): Boolean {
    return isInKobwebProject { it.type in limitTo }
}

/**
 * Useful test to see if a writing Kobweb Plugin action (like a refactor) should be allowed to run here.
 *
 * @param limitTo See the docs for [isInReadableKobwebProject] for more info.
 */
fun PsiElement.isInWritableKobwebProject(limitTo: Set<KobwebProjectType> = KobwebProjectTypes.Framework): Boolean {
    return isInKobwebProject { it.type in limitTo && it.source is KobwebProject.Source.Local }
}
