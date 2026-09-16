package com.varabyte.kobweb.intellij.util.kobweb

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import org.jetbrains.kotlin.psi.KtFile

enum class KobwebPluginState {
    /**
     * The Kobweb plugin is disabled for this project.
     *
     * This essentially means the user has installed the Kobweb plugin, but it should not be active for this project,
     * because there aren't any Kobweb modules inside of it.
     */
    DISABLED,

    /**
     * Indicates we started enabling the Kobweb plugin for this project, but a full Gradle sync is required to finish.
     */
    UNINITIALIZED,

    /**
     * The Kobweb plugin is enabled for this project.
     *
     * At this point, the project has been scanned, and we can query all found Kobweb metadata information.
     */
    INITIALIZED,
}

private const val KOBWEB_PLUGIN_STATE_PROPERTY = "kobweb-plugin-state"
var Project.kobwebPluginState: KobwebPluginState
    get() = PropertiesComponent.getInstance(this).getValue(KOBWEB_PLUGIN_STATE_PROPERTY, KobwebPluginState.DISABLED.name).let { KobwebPluginState.valueOf(it) }
    set(value) = PropertiesComponent.getInstance(this).setValue(KOBWEB_PLUGIN_STATE_PROPERTY, value.name)

val Project.isKobwebPluginEnabled get() = this.kobwebPluginState == KobwebPluginState.INITIALIZED

/**
 * A collection of useful sets of [KobwebProjectType]s.
 *
 * These can be useful to pass into the [isDeclaredInReadableKobwebProject], [isDeclaredInWritableKobwebProject],
 * [isUsedInReadableKobwebProject], and [isUsedInWritableKobwebProject] extension methods.
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
    return this.findKobwebProject()?.let { test(it) } ?: false
}

/**
 * Whether the target [PsiElement] is declared inside a [KobwebProject] or not.
 *
 * Note that it does not check if the element it _itself_ being used inside a Kobweb project, but rather if it is
 * declared there. If you want to check usage instead, use [isUsedInReadableKobwebProject].
 *
 * This is a useful test to see if a read-only Kobweb Plugin action (like an inspection) can run here.
 *
 * Finally, be aware that even writable projects will return true for this method. Since technically, it's a read-write
 * project.
 *
 * @param limitTo The [KobwebProject] types to limit this action to. By default, restricted to presentation types (that
 *   is, the parts of Kobweb that interact with the DOM). This default was chosen because this is by far the most
 *   common case, the kind of code that most people associate with Kobweb.
 */
fun PsiElement.isDeclaredInReadableKobwebProject(limitTo: Set<KobwebProjectType> = KobwebProjectTypes.Framework): Boolean {
    return isInKobwebProject { it.type in limitTo }
}

/**
 * Like [isDeclaredInReadableKobwebProject], but for code that can be edited.
 *
 * You may also wish to use [isUsedInWritableKobwebProject] if you care about where the code is called within rather than
 * where it is declared.
 *
 * If this method returns true, then the readable version will also return true (but not vice versa);
 *
 * This is a useful test to see if a Kobweb Plugin write action (like an inspection) can run here.
 *
 * @param limitTo See the docs for [isDeclaredInReadableKobwebProject] for more info.
 */
fun PsiElement.isDeclaredInWritableKobwebProject(limitTo: Set<KobwebProjectType> = KobwebProjectTypes.Framework): Boolean {
    return isInKobwebProject { it.type in limitTo && it.source is KobwebProject.Source.Local }
}

/**
 * Like [isDeclaredInReadableKobwebProject], but checking the file containing the element rather than where it was declared.
 *
 * This is useful if you are writing an inspection that cares about the _current_ file some code is in rather than the
 * source file that the [PsiElement] was declared in.
 */
fun PsiElement.isUsedInReadableKobwebProject(limitTo: Set<KobwebProjectType> = KobwebProjectTypes.Framework): Boolean {
    return this.containingFile.isDeclaredInReadableKobwebProject(limitTo)
}

/**
 * Like [isDeclaredInWritableKobwebProject], but checking the file containing the element rather than where it was declared.
 *
 * This is useful if you are writing an inspection that cares about the _current_ file some code is in rather than the
 * source file that the [PsiElement] was declared in.
 *
 * If this method returns true, then the readable version will also return true (but not vice versa);
 */
fun PsiElement.isUsedInWritableKobwebProject(limitTo: Set<KobwebProjectType> = KobwebProjectTypes.Framework): Boolean {
    return this.containingFile.isDeclaredInWritableKobwebProject(limitTo)
}
