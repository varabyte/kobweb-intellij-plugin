package com.varabyte.kobweb.intellij.util.kotlin

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.psiUtil.isIdentifier
import javax.lang.model.SourceVersion

fun FqName.child(relative: FqName): FqName {
    return if (isRoot) relative else FqName("${asString()}.${relative.asString()}")
}

/**
 * Whether this can be one segment of a package name, e.g. `example` in `com.example`: an identifier in both Java and
 * Kotlin that isn't a keyword in either, so Kotlin never needs backticks for it and Java code can still use it.
 */
fun String.isPackageSegment(): Boolean =
    SourceVersion.isIdentifier(this) && !SourceVersion.isKeyword(this) && isIdentifier()

/**
 * Turns this into a package segment, e.g. `My Site` into `mysite`, by lowercasing it and keeping only letters and
 * digits. Following Java's convention for package names made from domain names, a segment that would start with a
 * digit gets a `_` prefix (`_2048`), and a Java or Kotlin keyword gets a `_` suffix (`object_`, `new_`).
 *
 * @return null if this has no letters or digits.
 */
fun String.toPackageSegment(): String? {
    val segment = lowercase().filter { it.isLetterOrDigit() }.ifEmpty { return null }
    return when {
        segment.isPackageSegment() -> segment
        segment.first().isDigit() -> "_$segment"
        else -> "${segment}_"
    }
}
