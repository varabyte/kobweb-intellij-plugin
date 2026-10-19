package com.varabyte.kobweb.intellij.util.kobweb.worker

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.stubindex.KotlinSuperClassIndex
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

// NOTE: Everything in this file works on PSI alone, without using Kotlin's type resolution. The resolve extension uses
// these functions to decide which declarations to add to a module, but resolving types in that module first requires
// knowing all of its declarations (including ours), so using type resolution here would be circular.

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
    return constructors.isEmpty() || constructors.any { constructor ->
        !constructor.hasModifier(KtTokens.PRIVATE_KEYWORD)
                && !constructor.hasModifier(KtTokens.PROTECTED_KEYWORD)
                && constructor.valueParameters.all { it.hasDefaultValue() }
    }
}

/**
 * A user's `WorkerFactory<I, O>` implementation, which Kobweb generates a `Worker` class from.
 *
 * Use [from] to create one, which returns null if the target class isn't a valid `WorkerFactory` implementation.
 *
 * @property ktClass The factory class itself.
 * @property factoryFqName The fully qualified name of the factory class, e.g. `com.example.CalculatePiWorkerFactory`.
 * @property packageFqName The package the factory class lives in, e.g. `com.example`. This usually matches the parent
 *   of [factoryFqName], except for nested factory classes (e.g. `com.example.Outer.CalculatePiWorkerFactory`).
 * @property inputType The `I` type argument, as written in the source code.
 * @property outputType The `O` type argument, as written in the source code.
 * @property imports The import directives of the file containing the factory, e.g. `import com.example.data.Input`.
 *   Since [inputType] and [outputType] are written relative to them, we need to copy them into the synthesized file.
 */
class WorkerFactoryClass private constructor(
    val ktClass: KtClassOrObject,
    val factoryFqName: FqName,
    val packageFqName: FqName,
    val inputType: String,
    val outputType: String,
    val imports: List<String>,
) {
    companion object {
        fun from(ktClass: KtClassOrObject): WorkerFactoryClass? {
            if (!ktClass.canBeInstantiatedByWorker()) return null
            val factoryFqName = ktClass.fqName ?: return null
            val typeArguments = ktClass.findWorkerFactorySupertype()?.typeArgumentsAsTypes ?: return null
            if (typeArguments.size != 2) return null
            val inputType = typeArguments[0]?.text ?: return null
            val outputType = typeArguments[1]?.text ?: return null

            val file = ktClass.containingKtFile
            return WorkerFactoryClass(
                ktClass,
                factoryFqName,
                file.packageFqName,
                inputType,
                outputType,
                file.importDirectives.map { it.text },
            )
        }
    }
}

/**
 * Search the production sources of this module for its `WorkerFactory` implementation.
 *
 * Returns null if none are found, if there are multiple (as Kobweb requires there be exactly one), or if the one found
 * isn't valid (see [WorkerFactoryClass.from]).
 */
fun Module.findWorkerFactoryClass(): WorkerFactoryClass? {
    return KotlinSuperClassIndex
        .Helper[WORKER_FACTORY_CLASS_ID.shortClassName.asString(), project, getModuleScope(false)]
        .singleOrNull { it.findWorkerFactorySupertype() != null }
        ?.let { WorkerFactoryClass.from(it) }
}

/**
 * Returns this class as a [WorkerFactoryClass], but only if it's the one its module's worker is generated from.
 *
 * In other words, this returns null not only if this class isn't a valid `WorkerFactory` implementation, but also if
 * its module has multiple of them (in which case Kobweb doesn't generate a worker at all).
 */
fun KtClassOrObject.asWorkerFactoryClass(): WorkerFactoryClass? {
    return module?.findWorkerFactoryClass()?.takeIf { it.ktClass == this }
}
