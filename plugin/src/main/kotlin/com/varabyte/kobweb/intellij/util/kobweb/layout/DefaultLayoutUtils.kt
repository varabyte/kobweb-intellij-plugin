package com.varabyte.kobweb.intellij.util.kobweb.layout

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.util.kobweb.PackageShortcuts
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.psi.addImportIfNeeded
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * A default layout, i.e. one set by a `@file:Layout` annotation, which pages in its file's package (and subpackages)
 * use unless they say otherwise.
 *
 * @property annotation The `@file:Layout(...)` annotation that sets this default.
 * @property path The layout's path, as written in the annotation, e.g. ".components.layouts.BlogLayout".
 * @property layout The layout that [path] points at.
 * @property pagesPackage The package whose pages use this layout by default.
 */
class DefaultLayout(
    val annotation: KtAnnotationEntry,
    val path: String,
    val layout: LayoutDefinition,
    val pagesPackage: FqName,
)

private fun Module.collectDefaultLayouts(): List<DefaultLayout> {
    val kobwebProject = findKobwebProject() ?: return emptyList()
    // See `PsiElement.kobwebGroup` for why we treat an empty group as unknown
    val group = kobwebProject.group?.takeIf { it.isNotEmpty() }?.let(::FqName)
    // The pages package is only missing if IntelliJ restored a model saved by an older version of this plugin, so
    // assume Kobweb's default until the next Gradle sync
    val rootPagesPackage = PackageShortcuts.resolve(group, kobwebProject.pagesPackage ?: ".pages") ?: return emptyList()

    // Default layouts only apply to pages in the same module, so we don't need to search dependencies
    return KotlinAnnotationsIndex[LAYOUT_FQN.shortName().asString(), project, GlobalSearchScope.moduleScope(this)]
        .asSequence()
        .filter { entry -> entry.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.FILE }
        .filter { entry -> entry.isAnnotationOfType(LAYOUT_FQN) }
        .mapNotNull { entry ->
            // Kobweb ignores default layouts outside the pages package
            val pagesPackage = entry.containingKtFile.packageFqName
            if (!pagesPackage.isSubpackageOf(rootPagesPackage)) return@mapNotNull null

            val pathExpr = entry.layoutPathExpression ?: return@mapNotNull null
            val path = ElementManipulators.getValueText(pathExpr).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val layoutFqn = PackageShortcuts.resolve(group, path) ?: return@mapNotNull null
            val layout = entry.findLayout(layoutFqn.asString()) ?: return@mapNotNull null

            DefaultLayout(entry, path, layout, pagesPackage)
        }
        .toList()
}

/**
 * Returns all default layouts set in this module.
 */
private fun Module.findDefaultLayouts(): List<DefaultLayout> {
    return CachedValuesManager.getManager(project).getCachedValue(this) {
        CachedValueProvider.Result.create(
            collectDefaultLayouts(),
            PsiModificationTracker.getInstance(project),
            ProjectRootModificationTracker.getInstance(project),
        )
    }
}

/**
 * Returns the default layout set for this page's package, whether or not the page actually uses it, or null if there
 * isn't one.
 *
 * A page's default layout is the one set in the closest package containing it. Note that a page doesn't use it if it
 * specifies its own layout (with `@Layout`) or opts out of one (with `@NoLayout`); see [findDefaultLayout] for that.
 *
 * This always returns null for functions that aren't pages.
 */
fun KtNamedFunction.findPackageDefaultLayout(): DefaultLayout? {
    if (!isTopLevel) return null
    if (annotationEntries.none { it.isAnnotationOfType(PAGE_FQN) }) return null

    val pagePackage = containingKtFile.packageFqName
    return (module ?: return null).findDefaultLayouts()
        // Note: Kobweb itself checks this with a plain string prefix match, so a default layout set in "pages.blog"
        // would also apply to "pages.blogging". We match whole package segments instead, which is surely the intent.
        .filter { defaultLayout -> pagePackage.isSubpackageOf(defaultLayout.pagesPackage) }
        // If more than one package matches, the closest one wins. (If one package sets more than one default layout,
        // Kobweb picks one arbitrarily, so we break the tie by file path just so our choice doesn't change randomly.)
        .sortedWith(
            compareBy(
                { -it.pagesPackage.pathSegments().size },
                { it.annotation.containingFile.virtualFile?.path.orEmpty() },
            )
        )
        .firstOrNull()
}

/**
 * Returns the default layout that this page uses, or null if it doesn't use one.
 *
 * A page uses the default layout set in the closest package containing it (see [findPackageDefaultLayout]), unless it
 * specifies its own layout (with `@Layout`) or opts out of one (with `@NoLayout`).
 *
 * This always returns null for functions that aren't pages.
 */
fun KtNamedFunction.findDefaultLayout(): DefaultLayout? {
    if (annotationEntries.any { it.isAnnotationOfType(LAYOUT_FQN) || it.isAnnotationOfType(NO_LAYOUT_FQN) }) {
        return null
    }
    return findPackageDefaultLayout()
}

/**
 * A short, human-readable path for the file that sets this default layout, for showing to users (e.g. in tooltips and
 * inspection messages).
 *
 * Note that this isn't a real file path, and shouldn't be used to find the file. It's built from the file's package,
 * relative to the project's group, plus its name. For example, `DefaultLayout.kt` in package `com.example.pages.blog`
 * (with group `com.example`) is "pages/blog/DefaultLayout.kt". This tells files apart when many share a name (e.g. a
 * `DefaultLayout.kt` per package).
 */
val DefaultLayout.displayPath: String
    get() {
        val file = annotation.containingKtFile
        val packagePath = PackageShortcuts.relativize(file.kobwebGroup, file.packageFqName)
            .removePrefix(".")
            .replace('.', '/')
        return if (packagePath.isEmpty()) file.name else "$packagePath/${file.name}"
    }

/**
 * Annotates this page with [annotationText], right after its `@Page` annotation, which is where Kobweb's docs put
 * layout annotations, and imports [annotationFqn] if needed.
 */
private fun KtNamedFunction.addAnnotationAfterPage(annotationText: String, annotationFqn: FqName) {
    val pageAnnotation = annotationEntries.firstOrNull { it.isAnnotationOfType(PAGE_FQN) } ?: return
    val psiFactory = KtPsiFactory(project)

    // Separate the new annotation from `@Page` the same way that `@Page` is separated from the code after it
    val separator = (PsiTreeUtil.nextLeaf(pageAnnotation) as? PsiWhiteSpace)?.text ?: " "
    val parent = pageAnnotation.parent
    val annotation = parent.addAfter(psiFactory.createAnnotationEntry(annotationText), pageAnnotation)
    parent.addBefore(psiFactory.createWhiteSpace(separator), annotation)

    containingKtFile.addImportIfNeeded(annotationFqn)
}

/**
 * Opts this page out of its default layout, by annotating it with `@NoLayout`.
 *
 * The annotation is added right after the page's `@Page` annotation, which is where Kobweb's docs put layout
 * annotations.
 */
fun KtNamedFunction.addNoLayoutAnnotation() {
    addAnnotationAfterPage("@${NO_LAYOUT_FQN.shortName()}", NO_LAYOUT_FQN)
}

/**
 * Gives this page its own layout, by annotating it with `@Layout(path)`.
 *
 * The annotation is added right after the page's `@Page` annotation, which is where Kobweb's docs put layout
 * annotations.
 */
fun KtNamedFunction.addLayoutAnnotation(path: String) {
    val pathText = KtPsiFactory(project).createStringTemplate(path).text
    addAnnotationAfterPage("@${LAYOUT_FQN.shortName()}($pathText)", LAYOUT_FQN)
}
