package com.varabyte.kobweb.intellij.util.psi

import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.daemon.common.trimQuotes
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtProperty

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

// Used to live in `org.jetbrains.kotlin.js.translate.declaration.hasCustomGetter` but IJ removed it in 251.*
fun KtProperty.hasCustomGetter() = getter?.hasBody() ?: false

/**
 * Check if this annotatable declaration is annotated with a target and (optional) text value.
 */
fun KtAnnotated.isAnnotatedWith(classId: ClassId, expectedValue: String? = null): Boolean {
    fun KaAnnotationValue?.flattenStringValues(): List<String> = when (this) {
        is KaAnnotationValue.ConstantValue -> listOfNotNull(value.render().trimQuotes())
        is KaAnnotationValue.ArrayValue -> values.flatMap { it.flattenStringValues() }
        else -> emptyList()
    }

    val self = this
    val selfDeclaration = self as? KtDeclaration ?: return false
    return analyze(this) {
        val symbol = selfDeclaration.symbol

        symbol.annotations.any { annotation ->
            if (annotation.classId != classId) return@any false
            if (expectedValue == null) return@any true
            annotation.arguments.any { namedValue ->
                val annotationValues = namedValue.expression.flattenStringValues()
                expectedValue in annotationValues
            }
        }
    }
}

private val KOTLIN_SUPPRESS_CLASS_ID = ClassId.topLevel(FqName("kotlin.Suppress"))

fun KtAnnotated.isSuppressedWith(suppressKey: String): Boolean {
    return isAnnotatedWith(KOTLIN_SUPPRESS_CLASS_ID, suppressKey)
}