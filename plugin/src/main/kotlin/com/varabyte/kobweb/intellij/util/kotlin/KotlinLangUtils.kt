package com.varabyte.kobweb.intellij.util.kotlin

import org.jetbrains.kotlin.name.FqName

fun FqName.child(relative: FqName): FqName {
    return if (isRoot) relative else FqName("${asString()}.${relative.asString()}")
}
