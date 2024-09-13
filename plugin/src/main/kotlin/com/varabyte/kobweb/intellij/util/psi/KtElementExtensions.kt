package com.varabyte.kobweb.intellij.util.psi

import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtDeclaration

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
