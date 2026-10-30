package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.openapi.module.Module
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.api.API_FQN
import com.varabyte.kobweb.intellij.util.kobweb.api.API_PACKAGE_MAPPING_FQN
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiRouteResolver
import com.varabyte.kobweb.intellij.util.kobweb.layout.PAGE_FQN
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.psi.unescapedText
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * The string passed into this route override (e.g. `@Page` or `@Api`) or `@file:PackageMapping` annotation, e.g.
 * `"blog"` in `@Page("blog")`, if it's a plain string literal.
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
 * The annotation that declares a route of this kind, e.g. `@Page` for pages, which can also override the route Kobweb
 * would otherwise generate. (API streams are identified by their type instead, so they don't need one, but can use
 * `@Api` to override their routes.)
 */
val RouteKind.annotationFqn: FqName
    get() = when (this) {
        RouteKind.PAGE -> PAGE_FQN
        RouteKind.API, RouteKind.STREAM -> API_FQN
    }

/**
 * The `@file:PackageMapping` annotation that maps packages of declarations of this kind, as pages have their own (in
 * the frontend), separate from the one API endpoints and streams share (in the backend).
 */
val RouteKind.packageMappingFqn: FqName
    get() = when (this) {
        RouteKind.PAGE -> PACKAGE_MAPPING_FQN
        RouteKind.API, RouteKind.STREAM -> API_PACKAGE_MAPPING_FQN
    }

/**
 * Returns this declaration's annotation that declares a route of this [kind] (see [annotationFqn]), e.g. its `@Page`
 * annotation, if it has one.
 */
fun KtCallableDeclaration.findRouteAnnotation(kind: RouteKind): KtAnnotationEntry? =
    annotationEntries.firstOrNull { entry -> entry.isAnnotationOfType(kind.annotationFqn) }

/**
 * Returns the package that routes of this [kind] are relative to in this element's module, e.g. the pages package,
 * or null if it isn't in a Kobweb project (or we can't tell).
 */
fun PsiElement.findRootPackage(kind: RouteKind): FqName? = when (kind) {
    RouteKind.PAGE -> findPageRouteResolver()?.pagesPackage
    RouteKind.API, RouteKind.STREAM -> findApiRouteResolver()?.rootPackage
}

/**
 * Returns all `@file:PackageMapping` annotations in this module for routes of this [kind] (see [packageMappingFqn]).
 */
private fun Module.findPackageMappingAnnotations(kind: RouteKind): Sequence<KtAnnotationEntry> {
    val packageMappingFqn = kind.packageMappingFqn
    // Package mappings only apply to declarations in the same module, so we don't need to search dependencies
    return KotlinAnnotationsIndex[packageMappingFqn.shortName().asString(), project, GlobalSearchScope.moduleScope(this)]
        .asSequence()
        .filter { entry -> entry.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.FILE }
        .filter { entry -> entry.isAnnotationOfType(packageMappingFqn) }
}

/**
 * Returns the other `@file:PackageMapping` annotations that map the same package as this one.
 *
 * A package can only be mapped once, so if this returns anything, it's a mistake.
 *
 * @param kind The kind of routes this annotation maps packages for, which should match its type (see
 *   [packageMappingFqn]).
 */
fun KtAnnotationEntry.findDuplicatePackageMappings(kind: RouteKind): List<KtAnnotationEntry> {
    val pkg = containingKtFile.packageFqName
    return module?.findPackageMappingAnnotations(kind).orEmpty()
        .filter { entry -> entry != this && entry.containingKtFile.packageFqName == pkg }
        .toList()
}

/**
 * Returns the package mappings in this module that Kobweb uses, by the package they map.
 *
 * @param rootPackage The package that routes are relative to, e.g. "com.example.pages".
 * @param kind The kind of routes to collect package mappings for (see [packageMappingFqn]).
 */
internal fun Module.collectPackageMappings(rootPackage: FqName, kind: RouteKind): Map<FqName, String> {
    return findPackageMappingAnnotations(kind)
        // A package mapped more than once is reported as an error. Until the user fixes it, we sort by file path, so
        // which mapping we use doesn't change randomly
        .sortedBy { entry -> entry.containingFile.virtualFile?.path.orEmpty() }
        .mapNotNull { entry ->
            // Kobweb ignores package mappings outside the root package
            val pkg = entry.containingKtFile.packageFqName
            if (!pkg.isSubpackageOf(rootPackage)) return@mapNotNull null

            // Kobweb ignores invalid package mappings (which are reported as errors)
            val mapping = entry.routeValue
                ?.takeIf { mapping -> mapping.isNotBlank() }
                ?.takeIf { mapping -> RouteValueProblem.findInPackageMapping(mapping).none { it.isError } }
                ?: return@mapNotNull null
            pkg to mapping
        }
        .toMap()
}
