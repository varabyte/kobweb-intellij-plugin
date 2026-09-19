package com.varabyte.kobweb.intellij.util.compose

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val COMPOSE_WEB_CSS_PACKAGE = FqName("org.jetbrains.compose.web.css")
val STYLE_PROPERTY_VALUE_CLASS_ID = ClassId(COMPOSE_WEB_CSS_PACKAGE, Name.identifier("StylePropertyValue"))
