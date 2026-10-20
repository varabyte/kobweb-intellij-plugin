package com.varabyte.kobweb.intellij.util.kobweb.layout

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.varabyte.kobweb.intellij.util.kobweb.KOBWEB_METADATA_FRONTEND
import com.varabyte.kobweb.intellij.util.kobweb.PackageShortcuts
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

val LAYOUT_FQN = FqName("com.varabyte.kobweb.core.layout.Layout")
val NO_LAYOUT_FQN = FqName("com.varabyte.kobweb.core.layout.NoLayout")
val PAGE_FQN = FqName("com.varabyte.kobweb.core.Page")


/**
 * A layout function that a `@Layout` annotation can point at.
 *
 * @property fqn The fully qualified name of the layout function, e.g. "com.example.components.layouts.PageLayout"
 * @property function The layout function's declaration. This can be null if a library advertises a layout that we
 *   couldn't find a declaration for.
 */
class LayoutDefinition(val fqn: String, val function: KtNamedFunction?) {
    val shortName get() = fqn.substringAfterLast('.')
}

/**
 * The Gradle group associated with the Kobweb project this element lives in, if known.
 *
 * This is the base that relative `@Layout` paths are resolved against (see [PackageShortcuts]).
 *
 * Note: Kobweb doesn't support relative paths in projects without a group (where it would expand ".a.b" to ".a.b",
 * which never resolves), so we treat an empty group the same as an unknown one.
 */
val PsiElement.kobwebGroup: FqName? get() = findKobwebProject()?.group?.takeIf { it.isNotEmpty() }?.let(::FqName)

private fun KtAnnotated.hasAnnotation(fqn: FqName) = annotationEntries.any { it.isAnnotationOfType(fqn) }

/**
 * Returns true if this function is a layout, i.e. a top-level function tagged with `@Layout`.
 *
 * Note that a page can also be tagged with `@Layout`, which in that case means it lives inside a layout, not that it is
 * one, so pages are excluded.
 */
fun KtNamedFunction.isLayoutFunction(): Boolean {
    return isTopLevel && hasAnnotation(LAYOUT_FQN) && !hasAnnotation(PAGE_FQN)
}

private fun VirtualFile.readLayoutFqnsFromKobwebMetadata(): List<String> {
    // Kobweb libraries list the layouts they define in this file (see `FrontendData` in the Kobweb codebase)
    val frontendFile = findFileByRelativePath(KOBWEB_METADATA_FRONTEND) ?: return emptyList()
    return try {
        Json.parseToJsonElement(frontendFile.contentsToByteArray().decodeToString())
            .jsonObject["layouts"]?.jsonArray.orEmpty()
            .mapNotNull { (it as? JsonObject)?.get("fqn")?.jsonPrimitive?.content }
    } catch (_: Exception) {
        // Malformed metadata. Shouldn't happen, but there's nothing useful we can do about it here.
        emptyList()
    }
}

private fun Module.collectLayouts(): List<LayoutDefinition> {
    val scope = getModuleWithDependenciesAndLibrariesScope(false)

    val sourceLayouts = KotlinAnnotationsIndex[LAYOUT_FQN.shortName().asString(), project, scope]
        .asSequence()
        .mapNotNull { entry -> entry.getStrictParentOfType<KtNamedFunction>() }
        .filter { function -> function.isLayoutFunction() }
        .mapNotNull { function -> function.fqName?.let { LayoutDefinition(it.asString(), function) } }

    // Annotations in compiled dependencies aren't indexed, but Kobweb libraries tell us what layouts they define
    val libraryLayouts = OrderEnumerator.orderEntries(this).recursively().librariesOnly().classes().roots
        .asSequence()
        .flatMap { root -> root.readLayoutFqnsFromKobwebMetadata() }
        .map { fqn ->
            LayoutDefinition(fqn, KotlinTopLevelFunctionFqnNameIndex[fqn, project, scope].firstOrNull())
        }

    return (sourceLayouts + libraryLayouts).distinctBy { it.fqn }.toList()
}

/**
 * Returns all layouts visible to this element, i.e. those defined in its module plus any of its dependencies.
 */
fun PsiElement.findLayouts(): List<LayoutDefinition> {
    val module = module ?: return emptyList()
    return CachedValuesManager.getManager(project).getCachedValue(module) {
        CachedValueProvider.Result.create(
            module.collectLayouts(),
            PsiModificationTracker.getInstance(module.project),
            ProjectRootModificationTracker.getInstance(module.project),
        )
    }
}

/**
 * Returns the layout with the given fully qualified name visible to this element, or null if there isn't one.
 */
fun PsiElement.findLayout(fqn: String): LayoutDefinition? = findLayouts().firstOrNull { it.fqn == fqn }
