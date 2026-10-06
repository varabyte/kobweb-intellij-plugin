package com.varabyte.kobweb.intellij.util.kobweb.silk

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val SILK_PACKAGE = FqName("com.varabyte.kobweb.silk")
private val SILK_INIT_PACKAGE = SILK_PACKAGE.child(Name.identifier("init"))

val INIT_SILK_CLASS_ID = ClassId(SILK_INIT_PACKAGE, Name.identifier("InitSilk"))
