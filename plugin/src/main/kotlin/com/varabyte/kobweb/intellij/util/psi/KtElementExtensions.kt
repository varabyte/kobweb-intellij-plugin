package com.varabyte.kobweb.intellij.util.psi

import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

// Code adapted from https://kotlin.github.io/analysis-api/migrating-from-k1.html#using-analysis-api
private fun KtDeclaration.hasAnyAnnotation(vararg classIds: ClassId): Boolean {
    analyze(this) {
        val annotations = this@hasAnyAnnotation.symbol.annotations
        return classIds.any { it in annotations }
    }
}

/**
 * Returns true if the function is tagged with any one of the given annotations.
 *
 * @param key A key must be provided to prevent ambiguity errors, as multiple places can call `hasAnyAnnotation` with
 *   different annotation lists on the same target method.
 */
fun KtDeclaration.hasAnyAnnotation(key: Key<CachedValue<Boolean>>, vararg classIds: ClassId): Boolean {
    return CachedValuesManager.getCachedValue(this, key) {
        CachedValueProvider.Result.create(
            hasAnyAnnotation(*classIds),
            this.containingKtFile,
            ProjectRootModificationTracker.getInstance(project),
        )
    }
}

// region K1 legacy

/**
 * Determines whether this [KtAnnotationEntry] has the specified qualified name.
 * Careful: this does *not* currently take into account Kotlin type aliases (https://kotlinlang.org/docs/reference/type-aliases.html).
 *   Fortunately, type aliases are extremely uncommon for simple annotation types.
 */
private fun KtAnnotationEntry.fqNameMatches(fqName: String): Boolean {
    // For inspiration, see IDELightClassGenerationSupport.KtUltraLightSupportImpl.findAnnotation in the Kotlin plugin.
    val shortName = shortName?.asString() ?: return false
    return fqName.endsWith(".$shortName") && fqName == getQualifiedName()
}

/**
 * Computes the qualified name of this [KtAnnotationEntry].
 * Prefer to use [fqNameMatches], which checks the short name first and thus has better performance.
 */
private fun KtAnnotationEntry.getQualifiedName(): String? =
    analyze(BodyResolveMode.PARTIAL).get(BindingContext.ANNOTATION, this)?.fqName?.asString()

/**
 * Returns true if the function is tagged with any one of the given annotations.
 *
 * The annotation name must be fully-qualified, as in "androidx.compose.runtime.Composable".
 *
 * @param key A key must be provided to prevent ambiguity errors, as multiple places can call `hasAnyAnnotation` with
 *   different annotation lists on the same target method.
 */
internal fun KtAnnotated.hasAnyAnnotationK1(key: Key<CachedValue<Boolean>>, vararg annotationFqns: String): Boolean {
    // Not strictly required but results in a better error message if JB ever reports an issue:
    @Suppress("NAME_SHADOWING") val annotationFqns = annotationFqns.toList()
    // Code adapted from https://github.com/JetBrains/compose-multiplatform/blob/b501e0f794aecde9a6ce47cb4b5308939cbc7cc5/idea-plugin/src/main/kotlin/org/jetbrains/compose/desktop/ide/preview/locationUtils.kt#L135
    return CachedValuesManager.getCachedValue(this, key) {
        CachedValueProvider.Result.create(
            run {
                this.annotationEntries.any { annotationEntry ->
                    annotationFqns.any { fqName -> annotationEntry.fqNameMatches(fqName) }
                }
            },
            this.containingKtFile,
            ProjectRootModificationTracker.getInstance(project),
        )
    }
}

// endregion
