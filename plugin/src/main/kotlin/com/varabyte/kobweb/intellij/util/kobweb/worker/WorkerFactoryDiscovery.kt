package com.varabyte.kobweb.intellij.util.kobweb.worker

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.stubindex.KotlinSuperClassIndex
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

// NOTE: Everything in this file works on PSI alone, without resolving types. This is required, as we use it to
// synthesize declarations *during* resolution of the module that contains them.

private fun KtUserType.isWorkerFactoryReference(): Boolean {
    if (referencedName != WORKER_FACTORY_CLASS_ID.shortClassName.asString()) return false

    qualifier?.let { qualifier -> return qualifier.text == WORKER_PACKAGE.asString() }

    val file = containingKtFile
    if (file.packageFqName == WORKER_PACKAGE) return true
    return file.importDirectives.any { import ->
        if (import.isAllUnder) {
            import.importedFqName == WORKER_PACKAGE
        } else {
            import.importedFqName == WORKER_FACTORY_CLASS_ID.asSingleFqName() && import.aliasName == null
        }
    }
}

/**
 * Returns the `WorkerFactory<I, O>` supertype of this class, if it directly implements one.
 *
 * Note: Without type resolution, we can't detect classes that implement `WorkerFactory` indirectly (e.g. via an
 * intermediate base class) or via an import alias.
 */
private fun KtClassOrObject.findWorkerFactorySupertype(): KtUserType? {
    return superTypeListEntries
        .mapNotNull { it.typeReference?.typeElement as? KtUserType }
        .firstOrNull { it.isWorkerFactoryReference() }
}

/**
 * Whether Kobweb could generate code that instantiates this factory class, i.e. it is a concrete, non-private class
 * with an accessible constructor that takes no arguments.
 */
private fun KtClassOrObject.canBeInstantiatedByWorker(): Boolean {
    if (this !is KtClass || isInterface() || isEnum() || isAnnotation() || isInner() || isLocal) return false
    if (isPrivate() || hasModifier(KtTokens.ABSTRACT_KEYWORD) || hasModifier(KtTokens.SEALED_KEYWORD)) return false

    val constructors = listOfNotNull(primaryConstructor) + secondaryConstructors
    // No constructors declared means a default, public, empty one is provided
    if (constructors.isEmpty()) return true
    return constructors.any { constructor ->
        !constructor.hasModifier(KtTokens.PRIVATE_KEYWORD)
            && !constructor.hasModifier(KtTokens.PROTECTED_KEYWORD)
            && constructor.valueParameters.all { it.hasDefaultValue() }
    }
}

/**
 * Convert this class into a [WorkerFactoryInfo], or return null if it isn't a valid `WorkerFactory` implementation.
 */
fun KtClassOrObject.toWorkerFactoryInfo(): WorkerFactoryInfo? {
    if (!canBeInstantiatedByWorker()) return null
    val factoryFqName = fqName ?: return null
    val typeArguments = findWorkerFactorySupertype()?.typeArgumentsAsTypes ?: return null
    if (typeArguments.size != 2) return null
    val inputType = typeArguments[0]?.text ?: return null
    val outputType = typeArguments[1]?.text ?: return null

    val file = containingKtFile
    return WorkerFactoryInfo(
        factoryFqName,
        file.packageFqName,
        inputType,
        outputType,
        file.importDirectives.map { it.text },
    )
}

/**
 * Search the production sources of this module for the class that implements `WorkerFactory`.
 *
 * Returns null if none are found or if there are multiple (as Kobweb requires there be exactly one).
 */
fun Module.findWorkerFactoryClass(): KtClassOrObject? {
    return KotlinSuperClassIndex.Helper
        .get(WORKER_FACTORY_CLASS_ID.shortClassName.asString(), project, getModuleScope(false))
        .filter { it.findWorkerFactorySupertype() != null }
        .singleOrNull()
}
