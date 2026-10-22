package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.varabyte.kobweb.intellij.util.kobweb.layout.PAGE_FQN
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.qualifiedPagesPackage
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.psi.unescapedText
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

val PACKAGE_MAPPING_FQN = FqName("com.varabyte.kobweb.core.PackageMapping")

/**
 * The string passed into this `@Page` or `@file:PackageMapping` annotation, e.g. `"blog"` in `@Page("blog")`, if it's
 * a plain string literal.
 *
 * Returns null if there's no argument, or if we can't tell its value (e.g. it's a constant, or a string with template
 * expressions). Check [KtAnnotationEntry.getValueArguments] first if you need to tell those cases apart.
 */
val KtAnnotationEntry.routeValueExpression: KtStringTemplateExpression?
    get() {
        val stringExpr = valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
        return stringExpr?.takeIf { !it.hasInterpolation() }
    }

/**
 * The value of [routeValueExpression], e.g. "blog" in `@Page("blog")`, with any escape sequences decoded.
 */
val KtAnnotationEntry.routeValue: String? get() = routeValueExpression?.unescapedText

/**
 * Returns all `@file:PackageMapping` annotations in this module.
 */
private fun Module.findPackageMappingAnnotations(): Sequence<KtAnnotationEntry> {
    // Package mappings only apply to pages in the same module, so we don't need to search dependencies
    return KotlinAnnotationsIndex[PACKAGE_MAPPING_FQN.shortName().asString(), project, GlobalSearchScope.moduleScope(this)]
        .asSequence()
        .filter { entry -> entry.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.FILE }
        .filter { entry -> entry.isAnnotationOfType(PACKAGE_MAPPING_FQN) }
}

/**
 * Returns the other `@file:PackageMapping` annotations that map the same package as this one.
 *
 * A package can only be mapped once, so if this returns anything, it's a mistake.
 */
fun KtAnnotationEntry.findDuplicatePackageMappings(): List<KtAnnotationEntry> {
    val pkg = containingKtFile.packageFqName
    return module?.findPackageMappingAnnotations().orEmpty()
        .filter { entry -> entry != this && entry.containingKtFile.packageFqName == pkg }
        .toList()
}

private fun Module.collectPackageMappings(pagesPackage: FqName): Map<FqName, String> {
    return findPackageMappingAnnotations()
        // A package mapped more than once is reported as an error. Until the user fixes it, we sort by file path, so
        // which mapping we use doesn't change randomly
        .sortedBy { entry -> entry.containingFile.virtualFile?.path.orEmpty() }
        .mapNotNull { entry ->
            // Kobweb ignores package mappings outside the pages package
            val pkg = entry.containingKtFile.packageFqName
            if (!pkg.isSubpackageOf(pagesPackage)) return@mapNotNull null

            val mapping = entry.routeValue ?: return@mapNotNull null
            pkg to mapping
        }
        .toMap()
}

/**
 * Returns the resolver for routes of pages in this module, or null if this module isn't a Kobweb project (or we
 * can't tell where its pages live).
 */
private fun Module.findPageRouteResolver(): PageRouteResolver? {
    return CachedValuesManager.getManager(project).getCachedValue(this) {
        val pagesPackage = findKobwebProject()?.qualifiedPagesPackage
        CachedValueProvider.Result.create(
            pagesPackage?.let { PageRouteResolver(it, collectPackageMappings(it)) },
            PsiModificationTracker.getInstance(project),
            ProjectRootModificationTracker.getInstance(project),
        )
    }
}

/**
 * Returns the resolver for routes of pages in this element's module, or null if it isn't in a Kobweb project (or we
 * can't tell where its pages live).
 */
fun PsiElement.findPageRouteResolver(): PageRouteResolver? = module?.findPageRouteResolver()

/**
 * The name of the file this page lives in, without its extension, which Kobweb generates its slug from.
 */
val KtNamedFunction.pageFileName: String get() = FileUtilRt.getNameWithoutExtension(containingKtFile.name)

/**
 * Returns the route that Kobweb generates for [page], if it had the given route override (instead of its own).
 *
 * This is useful for comparing alternatives, e.g. to check if a route override is redundant.
 */
fun PageRouteResolver.resolve(page: KtNamedFunction, routeOverride: String?): String? {
    return resolve(page.containingKtFile.packageFqName, page.pageFileName, routeOverride)
}

/**
 * Returns the route that Kobweb generates for this page, e.g. "/blog/post" or "/users/{user}".
 *
 * See [PageRouteResolver] for details.
 *
 * @return null if this function isn't a page, if Kobweb skips it (e.g. it lives outside the pages package), or if we
 *   can't tell its route (e.g. its route override is a constant).
 */
fun KtNamedFunction.findRoute(): String? {
    if (!isTopLevel) return null
    val pageAnnotation = annotationEntries.firstOrNull { it.isAnnotationOfType(PAGE_FQN) } ?: return null
    val routeOverride = if (pageAnnotation.valueArguments.isEmpty()) null else {
        pageAnnotation.routeValue ?: return null
    }

    return findPageRouteResolver()?.resolve(this, routeOverride)
}
