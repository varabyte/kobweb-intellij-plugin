package com.varabyte.kobweb.intellij.util.kobweb.style

import com.varabyte.kobweb.intellij.util.psi.resolveToCallableId
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression

private val SILK_STYLE_PACKAGE = FqName("com.varabyte.kobweb.silk.style")
private val SILK_STYLE_ANIMATION_PACKAGE = SILK_STYLE_PACKAGE.child(Name.identifier("animation"))
private val CSS_STYLE_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("CssStyle"))
private val CSS_STYLE_VARIANT_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("CssStyleVariant"))
private val KEYFRAMES_CLASS_ID = ClassId(SILK_STYLE_ANIMATION_PACKAGE, Name.identifier("Keyframes"))

private val CSS_STYLE_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("CssStyle"))
private val CSS_STYLE_BASE_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("base"))
private val ADD_VARIANT_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("addVariant"))
private val ADD_VARIANT_BASE_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("addVariantBase"))
private val EXTENDED_BY_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("extendedBy"))
private val EXTENDED_BY_BASE_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("extendedByBase"))

/**
 * Extract a [ClassId] from a type if this type represents one of Kobweb's singleton style objects.
 *
 * These are objects that are expected to be public and declared at the top level of a file (or inside a top-level
 * object) so that the generated `main` function can find and register them at startup.
 */
context(kaSession: KaSession)
val KaType.styleSingletonClassId: ClassId? get() = with(kaSession) {
    expandedSymbol?.classId?.takeIf { it in setOf(CSS_STYLE_CLASS_ID, CSS_STYLE_VARIANT_CLASS_ID, KEYFRAMES_CLASS_ID) }
}

context(kaSession: KaSession)
val KtCallExpression.styleSingletonCallableId: CallableId? get() {
    return resolveToCallableId()?.takeIf { it in setOf(
        CSS_STYLE_CALLABLE_ID,
        CSS_STYLE_BASE_CALLABLE_ID,
        ADD_VARIANT_CALLABLE_ID,
        ADD_VARIANT_BASE_CALLABLE_ID,
        EXTENDED_BY_CALLABLE_ID,
        EXTENDED_BY_BASE_CALLABLE_ID,
    )}
}
