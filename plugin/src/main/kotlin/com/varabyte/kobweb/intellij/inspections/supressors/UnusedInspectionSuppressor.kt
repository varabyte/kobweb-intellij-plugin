package com.varabyte.kobweb.intellij.inspections.supressors

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.KobwebProjectTypes
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiStreamRoute
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.worker.asWorkerFactoryClass
import com.varabyte.kobweb.intellij.util.kobweb.worker.computeWorkerClassId
import com.varabyte.kobweb.intellij.util.psi.hasAnyAnnotation
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

private val SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH = arrayOf(
    ClassId.fromString("com/varabyte/kobweb/api/Api"),
    ClassId.fromString("com/varabyte/kobweb/api/init/InitApi"),
    ClassId.fromString("com/varabyte/kobweb/core/App"),
    ClassId.fromString("com/varabyte/kobweb/core/layout/Layout"),
    ClassId.fromString("com/varabyte/kobweb/core/Page"),
    ClassId.fromString("com/varabyte/kobweb/core/init/InitKobweb"),
    ClassId.fromString("com/varabyte/kobweb/core/init/InitRoute"),
    ClassId.fromString("com/varabyte/kobweb/silk/init/InitSilk"),
)

/**
 * Whether this is a function with an annotation that tells Kobweb to generate code that calls it, e.g. `@Page`.
 */
private fun PsiElement.isUsedByGeneratedFrameworkCode(): Boolean {
    if (!isInReadableKobwebProject()) return false
    // Originally, only `parent` was checked, but at some point it became necessary to check `this` too
    val ktFunction = parent as? KtNamedFunction
        ?: this as? KtNamedFunction
        ?: return false

    return analyze(ktFunction) { ktFunction.hasAnyAnnotation(*SUPPRESS_UNUSED_WHEN_ANNOTATED_WITH) }
}

/**
 * Whether this is an `ApiStream` property that Kobweb registers, which the generated code references.
 */
private fun PsiElement.isUsedByGeneratedApiStreamCode(): Boolean {
    val property = parent as? KtProperty
        ?: this as? KtProperty
        ?: return false
    if (!property.isInReadableKobwebProject()) return false
    return analyze(property) { property.findApiStreamRoute() } != null
}

/**
 * Whether this is a worker module's `WorkerFactory` implementation, which the generated `Worker` class instantiates.
 */
private fun PsiElement.isUsedByGeneratedWorkerCode(): Boolean {
    if (!isInReadableKobwebProject(KobwebProjectTypes.WorkerOnly)) return false
    val ktClass = parent as? KtClassOrObject
        ?: this as? KtClassOrObject
        ?: return false

    // Consider this class used if it is a worker factory used to generate a worker (e.g. if there are multiple
    // factories, Kobweb wouldn't generate anything, so in that case, none of them are used)
    val factoryClass = ktClass.asWorkerFactoryClass() ?: return false
    val fqcnOverride = findKobwebProject()?.worker?.fqcn
    return factoryClass.computeWorkerClassId(fqcnOverride) != null
}

/**
 * Suppress the "Unused code" inspection, when we know that Kobweb will generate code that uses it.
 */
class UnusedInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != "unused") return false
        return element.isUsedByGeneratedFrameworkCode()
            || element.isUsedByGeneratedApiStreamCode()
            || element.isUsedByGeneratedWorkerCode()
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String) = emptyArray<SuppressQuickFix>()
}
