package com.varabyte.kobweb.intellij.util.kobweb.silk

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val SILK_PACKAGE = FqName("com.varabyte.kobweb.silk")
private val SILK_INIT_PACKAGE = SILK_PACKAGE.child(Name.identifier("init"))
private val SILK_THEME_PACKAGE = SILK_PACKAGE.child(Name.identifier("theme"))
private val SILK_THEME_COLORS_PACKAGE = SILK_THEME_PACKAGE.child(Name.identifier("colors"))

val INIT_SILK_CLASS_ID = ClassId(SILK_INIT_PACKAGE, Name.identifier("InitSilk"))

val COLOR_MODE_CLASS_ID = ClassId(SILK_THEME_COLORS_PACKAGE, Name.identifier("ColorMode"))
val COLOR_MODE_COMPANION_CLASS_ID = ClassId(SILK_THEME_COLORS_PACKAGE, Name.identifier("ColorMode.Companion"))