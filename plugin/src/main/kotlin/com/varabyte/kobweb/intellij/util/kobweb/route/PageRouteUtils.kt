package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.ElementManipulators
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.varabyte.kobweb.intellij.util.kobweb.layout.PAGE_FQN
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.qualifiedPagesPackage
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
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
 * The value of this annotation's first argument, e.g. "blog" in `@Page("blog")`, if it's a plain string literal.
 *
 * Returns null if there's no argument, or if we can't tell its value (e.g. it's a constant, or a string with template
 * expressions). Check [KtAnnotationEntry.getValueArguments] first if you need to tell those cases apart.
 */
private val KtAnnotationEntry.stringLiteralArgument: String?
    get() {
        val stringExpr = valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
        return stringExpr?.takeIf { !it.hasInterpolation() }?.let(ElementManipulators::getValueText)
    }

private fun Module.collectPackageMappings(pagesPackage: FqName): Map<FqName, String> {
    // Package mappings only apply to pages in the same module, so we don't need to search dependencies
    return KotlinAnnotationsIndex[PACKAGE_MAPPING_FQN.shortName().asString(), project, GlobalSearchScope.moduleScope(this)]
        .asSequence()
        .filter { entry -> entry.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.FILE }
        .filter { entry -> entry.isAnnotationOfType(PACKAGE_MAPPING_FQN) }
        // If one package is mapped more than once, Kobweb picks one arbitrarily, so we sort by file path just so our
        // choice doesn't change randomly
        .sortedBy { entry -> entry.containingFile.virtualFile?.path.orEmpty() }
        .mapNotNull { entry ->
            // Kobweb ignores package mappings outside the pages package
            val pkg = entry.containingKtFile.packageFqName
            if (!pkg.isSubpackageOf(pagesPackage)) return@mapNotNull null

            val mapping = entry.stringLiteralArgument ?: return@mapNotNull null
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
        pageAnnotation.stringLiteralArgument ?: return null
    }

    val file = containingKtFile
    return module?.findPageRouteResolver()
        ?.resolve(file.packageFqName, FileUtilRt.getNameWithoutExtension(file.name), routeOverride)
}
