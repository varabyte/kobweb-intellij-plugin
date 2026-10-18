package com.varabyte.kobweb.intellij.util.kobweb.worker

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val WORKER_PACKAGE = FqName("com.varabyte.kobweb.worker")

val WORKER_CLASS_ID = ClassId(WORKER_PACKAGE, Name.identifier("Worker"))
val WORKER_FACTORY_CLASS_ID = ClassId(WORKER_PACKAGE, Name.identifier("WorkerFactory"))
val WORKER_CONTEXT_CLASS_ID = ClassId(WORKER_PACKAGE, Name.identifier("WorkerContext"))
val ATTACHMENTS_CLASS_ID = ClassId(WORKER_PACKAGE, Name.identifier("Attachments"))
val REMEMBER_WORKER_CALLABLE_ID = CallableId(WORKER_PACKAGE, Name.identifier("rememberWorker"))
