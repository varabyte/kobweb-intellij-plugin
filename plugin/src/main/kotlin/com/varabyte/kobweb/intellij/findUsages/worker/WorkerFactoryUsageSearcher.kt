package com.varabyte.kobweb.intellij.findUsages.worker

import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.util.Processor
import com.varabyte.kobweb.intellij.resolve.worker.findSynthesizedWorkerClass
import com.varabyte.kobweb.intellij.util.kobweb.KobwebProjectTypes
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.worker.asWorkerFactoryClass
import com.varabyte.kobweb.intellij.util.kobweb.worker.computeWorkerClassId
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtClassOrObject

/**
 * When finding usages of a `WorkerFactory` implementation, also include usages of the `Worker` class generated from it.
 *
 * Users never reference their factory directly. Instead, they reference the `Worker` class Kobweb generates from it, so
 * without this, a worker factory would always appear to be unused.
 *
 * NOTE: We intentionally do this with a [CustomUsageSearcher], which is only used by Find Usages, instead of a
 * `ReferencesSearch` executor, which is also used by Rename. Otherwise, renaming a factory would also rename every
 * reference to its worker class to the factory's new name.
 */
class WorkerFactoryUsageSearcher : CustomUsageSearcher() {
    override fun processElementUsages(element: PsiElement, processor: Processor<in Usage>, options: FindUsagesOptions) {
        val workerClass = ReadAction.compute<PsiElement?, Throwable> {
            if (!element.isInReadableKobwebProject(KobwebProjectTypes.WorkerOnly)) return@compute null
            val factoryClass = (element as? KtClassOrObject)?.asWorkerFactoryClass() ?: return@compute null
            val fqcnOverride = element.findKobwebProject()?.worker?.fqcn
            val workerClassId = factoryClass.computeWorkerClassId(fqcnOverride) ?: return@compute null
            analyze(factoryClass.ktClass) { factoryClass.findSynthesizedWorkerClass(workerClassId) }
        } ?: return

        ReferencesSearch.search(workerClass, options.searchScope).forEach(Processor { reference ->
            processor.process(UsageInfo2UsageAdapter(UsageInfo(reference)))
        })
    }
}
