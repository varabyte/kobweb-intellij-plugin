package com.varabyte.kobweb.intellij.util.kobweb.worker

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val WORKER_PACKAGE = FqName("com.varabyte.kobweb.worker")

val WORKER_CLASS_ID = ClassId(WORKER_PACKAGE, Name.identifier("Worker"))
val REMEMBER_WORKER_CALLABLE_ID = CallableId(WORKER_PACKAGE, Name.identifier("rememberWorker"))
