package com.varabyte.kobweb.intellij.util.kobweb.worker

import com.varabyte.truthish.assertThat
import org.jetbrains.kotlin.name.FqName
import org.junit.Test

class WorkerClassIdTest {
    private fun computeWorkerFqcn(factoryFqcn: String, fqcnOverride: String? = null): String? {
        val factoryFqName = FqName(factoryFqcn)
        val dummyWorkerFactoryInfo = WorkerFactoryInfo(factoryFqName, factoryFqName.parent(), "String", "String", emptyList())

        return dummyWorkerFactoryInfo.computeWorkerClassId(fqcnOverride)?.asFqNameString()
    }

    // See also: the examples in the `WorkerBlock.fqcn` documentation in Kobweb
    @Test
    fun workerNameFollowsFqcnOverrideRules() {
        val factory = "com.example.PiCalculatorWorkerFactory"
        assertThat(computeWorkerFqcn(factory)).isEqualTo("com.example.PiCalculatorWorker")
        assertThat(computeWorkerFqcn(factory, "PiWorker")).isEqualTo("PiWorker")
        assertThat(computeWorkerFqcn(factory, "com.mysite.PiWorker")).isEqualTo("com.mysite.PiWorker")
        assertThat(computeWorkerFqcn(factory, ".PiWorker")).isEqualTo("com.example.PiWorker")
        assertThat(computeWorkerFqcn(factory, "com.mysite.")).isEqualTo("com.mysite.PiCalculatorWorker")
        assertThat(computeWorkerFqcn(factory, ".mysite.PiWorker")).isEqualTo("com.example.mysite.PiWorker")
        assertThat(computeWorkerFqcn(factory, ".mysite.")).isEqualTo("com.example.mysite.PiCalculatorWorker")
        assertThat(computeWorkerFqcn(factory, ".")).isEqualTo("com.example.PiCalculatorWorker")

        // Factories in the root package
        val rootFactory = "PiCalculatorWorkerFactory"
        assertThat(computeWorkerFqcn(rootFactory)).isEqualTo("PiCalculatorWorker")
        assertThat(computeWorkerFqcn(rootFactory, ".mysite.PiWorker")).isEqualTo("mysite.PiWorker")
    }

    @Test
    fun factoryNameMustEndWithWorkerFactoryUnlessClassNameOverridden() {
        val factory = "com.example.PiCalculator"
        assertThat(computeWorkerFqcn(factory)).isNull()
        assertThat(computeWorkerFqcn(factory, "com.mysite.")).isNull()
        assertThat(computeWorkerFqcn(factory, ".PiWorker")).isEqualTo("com.example.PiWorker")

        // A factory named just "WorkerFactory" doesn't have a name to derive the worker's from, regardless of package
        assertThat(computeWorkerFqcn("WorkerFactory")).isNull()
        assertThat(computeWorkerFqcn("com.example.WorkerFactory")).isNull()
    }
}
