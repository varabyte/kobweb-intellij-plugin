package com.varabyte.kobweb.intellij.util.compose

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val COMPOSE_RUNTIME_PACKAGE = FqName("androidx.compose.runtime")

val COMPOSABLE_CLASS_ID = ClassId(COMPOSE_RUNTIME_PACKAGE, Name.identifier("Composable"))
val MUTABLE_STATE_CLASS_ID = ClassId(COMPOSE_RUNTIME_PACKAGE, Name.identifier("MutableState"))

val REMEMBER_CALLABLE_ID = CallableId(COMPOSE_RUNTIME_PACKAGE, Name.identifier("remember"))
