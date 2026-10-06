package com.varabyte.kobweb.intellij.util.kobweb.compose

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val COMPOSE_RUNTIME_PACKAGE = FqName("androidx.compose.runtime")

val COMPOSABLE_CLASS_ID = ClassId(COMPOSE_RUNTIME_PACKAGE, Name.identifier("Composable"))
