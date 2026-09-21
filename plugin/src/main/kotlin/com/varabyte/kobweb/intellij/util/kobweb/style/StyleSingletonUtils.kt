package com.varabyte.kobweb.intellij.util.kobweb.style

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val SILK_STYLE_PACKAGE = FqName("com.varabyte.kobweb.silk.style")
private val SILK_STYLE_ANIMATION_PACKAGE = SILK_STYLE_PACKAGE.child(Name.identifier("animation"))
private val CSS_STYLE_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("CssStyle"))
private val CSS_STYLE_VARIANT_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("CssStyleVariant"))
private val KEYFRAMES_CLASS_ID = ClassId(SILK_STYLE_ANIMATION_PACKAGE, Name.identifier("Keyframes"))

private val STYLE_SINGLETON_CLASS_IDS = setOf(CSS_STYLE_CLASS_ID, CSS_STYLE_VARIANT_CLASS_ID, KEYFRAMES_CLASS_ID)

/**
 * Extract a [ClassId] from a type if this type represents one of Kobweb's singleton style objects.
 *
 * These are objects that are expected to be public and declared at the top level of a file (or inside a top-level
 * object) so that the generated `main` function can find and register them at startup.
 */
context(kaSession: KaSession)
val KaType.styleSingletonClassId: ClassId? get() = with(kaSession) {
    expandedSymbol?.classId?.takeIf { it in STYLE_SINGLETON_CLASS_IDS }
}
