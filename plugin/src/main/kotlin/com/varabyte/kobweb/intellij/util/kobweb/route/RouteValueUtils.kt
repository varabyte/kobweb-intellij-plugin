package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.openapi.module.Module
import com.intellij.psi.search.GlobalSearchScope
import com.varabyte.kobweb.intellij.util.kobweb.api.API_PACKAGE_MAPPING_FQN
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.psi.unescapedText
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.psi.KtAnnotationEntry
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
 * Returns all `@file:PackageMapping` annotations in this module.
 *
 * @param packageMappingFqn Which `PackageMapping` annotation to look for, as pages and API endpoints each have their own
 *   (see [PACKAGE_MAPPING_FQN] and [API_PACKAGE_MAPPING_FQN]).
 */
private fun Module.findPackageMappingAnnotations(packageMappingFqn: FqName): Sequence<KtAnnotationEntry> {
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
 * @param packageMappingFqn See [findPackageMappingAnnotations]. This should be the type of this annotation.
 */
fun KtAnnotationEntry.findDuplicatePackageMappings(packageMappingFqn: FqName): List<KtAnnotationEntry> {
    val pkg = containingKtFile.packageFqName
    return module?.findPackageMappingAnnotations(packageMappingFqn).orEmpty()
        .filter { entry -> entry != this && entry.containingKtFile.packageFqName == pkg }
        .toList()
}

/**
 * Returns the package mappings in this module that Kobweb uses, by the package they map.
 *
 * @param packageMappingFqn See [findPackageMappingAnnotations].
 * @param rootPackage The package that routes are relative to, e.g. "com.example.pages".
 */
internal fun Module.collectPackageMappings(rootPackage: FqName, packageMappingFqn: FqName): Map<FqName, String> {
    return findPackageMappingAnnotations(packageMappingFqn)
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
