package com.varabyte.kobweb.intellij.test.fixtures

import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.model.WorkerBlock

/** A test case for code inside a Kobweb worker module. */
abstract class KobwebWorkerTestCase : KobwebProjectTestCase(KobwebProjectType.Worker) {
    /** Simulate the user setting `kobweb.worker.fqcn` in their build script (and then syncing). */
    protected fun setWorkerFqcn(fqcn: String?) = registerKobwebProject(worker = WorkerBlock(fqcn))
}
