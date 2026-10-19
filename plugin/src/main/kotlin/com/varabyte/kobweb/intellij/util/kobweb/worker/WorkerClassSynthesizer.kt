package com.varabyte.kobweb.intellij.util.kobweb.worker

import com.varabyte.kobweb.intellij.util.kotlin.child
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

// The logic in this file mirrors Kobweb's `WorkerProcessor` (a KSP processor), which generates a `Worker` class for the
// `WorkerFactory` implementation found in a Kobweb worker module. We synthesize that class ourselves so that the IDE
// understands it even before the user builds their project (and so it stays accurate as the user edits their code).
//
// `WorkerClassSynthesizerParityTest` verifies that what we generate here matches what the real processor generates.

private const val WORKER_SUFFIX = "Worker"
private const val WORKER_FACTORY_SUFFIX = "${WORKER_SUFFIX}Factory"

/**
 * Compute the [ClassId] of the `Worker` class that Kobweb would generate for this factory.
 *
 * @param fqcnOverride The value of `kobweb.worker.fqcn`, if set. See the `WorkerBlock.fqcn` documentation in Kobweb for
 *   examples of how this value is interpreted.
 *
 * @return null if Kobweb would refuse to generate a class, i.e. if the factory's name doesn't end with "WorkerFactory"
 *   and no class name override was provided.
 */
fun WorkerFactoryClass.computeWorkerClassId(fqcnOverride: String?): ClassId? {
    return WorkerFactoryClass.computeWorkerClassId(factoryFqName, packageFqName, fqcnOverride)
}

/**
 * Like the [WorkerFactoryClass.computeWorkerClassId] extension, but working on names alone (which is useful for tests).
 *
 * @param factoryFqName See [WorkerFactoryClass.factoryFqName].
 * @param packageFqName See [WorkerFactoryClass.packageFqName].
 */
fun WorkerFactoryClass.Companion.computeWorkerClassId(
    factoryFqName: FqName,
    packageFqName: FqName,
    fqcnOverride: String?
): ClassId? {
    val classNameOverride = fqcnOverride?.substringAfterLast('.')?.takeIf { it.isNotBlank() }
    if (classNameOverride == null) {
        val factoryName = factoryFqName.shortName().asString()
        if (!factoryName.endsWith(WORKER_FACTORY_SUFFIX) || factoryName == WORKER_FACTORY_SUFFIX) return null
    }

    val workerPackage = when {
        fqcnOverride == null -> packageFqName
        // A bare class name, e.g. "PiWorker", is a fully qualified name in the root package
        '.' !in fqcnOverride -> FqName.ROOT
        else -> {
            val packageOverride = fqcnOverride.substringBeforeLast('.')
            when {
                packageOverride.isEmpty() -> packageFqName
                // A leading "." means the package is relative to the factory's package
                packageOverride.startsWith('.') -> packageFqName.child(FqName(packageOverride.removePrefix(".")))
                else -> FqName(packageOverride)
            }
        }
    }
    val workerClassName = classNameOverride ?: factoryFqName.shortName().asString().removeSuffix(
        WORKER_FACTORY_SUFFIX.removePrefix(WORKER_SUFFIX)
    )

    return ClassId(workerPackage, Name.identifier(workerClassName))
}

/**
 * Generate the source for a `Worker` class with the same public API as the one Kobweb would generate.
 *
 * Only the API is meaningful here. Implementations are stubbed out, since this code is never compiled or run.
 */
fun WorkerFactoryClass.synthesizeWorkerClassSource(workerClassId: ClassId): String {
    val factory = this
    val workerPackage = workerClassId.packageFqName
    val inputType = factory.inputType
    val outputType = factory.outputType

    return buildString {
        appendLine("// Synthesized by the Kobweb IntelliJ plugin, mirroring the class Kobweb generates from `${factory.factoryFqName}`")
        if (!workerPackage.isRoot) {
            appendLine()
            appendLine("package $workerPackage")
        }

        val imports = buildList {
            addAll(factory.imports)
            // The factory may refer to types in its own package without importing them
            if (!factory.packageFqName.isRoot && factory.packageFqName != workerPackage) {
                add("import ${factory.packageFqName}.*")
            }
        }
        if (imports.isNotEmpty()) {
            appendLine()
            imports.forEach { appendLine(it) }
        }

        appendLine()
        appendLine(
            """
            class ${workerClassId.shortClassName}(override var onOutput: ${WORKER_CONTEXT_CLASS_ID.asFqNameString()}.(output: $outputType) -> Unit = {}) :
                ${WORKER_CLASS_ID.asFqNameString()}<$inputType, $outputType> {
                override fun postInput(input: $inputType, attachments: ${ATTACHMENTS_CLASS_ID.asFqNameString()}) { TODO() }
                override fun terminate() { TODO() }
            }
            """.trimIndent()
        )
    }
}
