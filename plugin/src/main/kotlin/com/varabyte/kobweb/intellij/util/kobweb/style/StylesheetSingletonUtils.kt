package com.varabyte.kobweb.intellij.util.kobweb.style

import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.psi.resolveToCallableId
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

// This file containts utilities to help with Kobweb's top-level singleton properties that read style modifiers and put
// their values into the site's stylesheet.

private val SILK_STYLE_PACKAGE = FqName("com.varabyte.kobweb.silk.style")
private val SILK_STYLE_ANIMATION_PACKAGE = SILK_STYLE_PACKAGE.child(Name.identifier("animation"))
val CSS_STYLE_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("CssStyle"))
private val CSS_STYLE_COMPANION_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("CssStyle.Companion"))
private val CSS_STYLE_SCOPE_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("StyleScope"))

private val CSS_STYLE_VARIANT_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("CssStyleVariant"))
private val KEYFRAMES_CLASS_ID = ClassId(SILK_STYLE_ANIMATION_PACKAGE, Name.identifier("Keyframes"))

private val STYLE_SINGLETON_CLASS_IDS = setOf(CSS_STYLE_CLASS_ID, CSS_STYLE_VARIANT_CLASS_ID, KEYFRAMES_CLASS_ID)

private val CSS_STYLE_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("CssStyle"))
private val CSS_STYLE_SCOPE_BASE_CALLABLE_ID = CallableId(CSS_STYLE_SCOPE_CLASS_ID, Name.identifier("base"))
private val CSS_STYLE_BASE_EXTENSION_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("base"))
private val ADD_VARIANT_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("addVariant"))
private val ADD_VARIANT_BASE_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("addVariantBase"))
private val EXTENDED_BY_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("extendedBy"))
private val EXTENDED_BY_BASE_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("extendedByBase"))

private val STYLE_SINGLETON_CALLABLE_IDS = setOf(
    CSS_STYLE_CALLABLE_ID,
    CSS_STYLE_BASE_EXTENSION_CALLABLE_ID,
    CSS_STYLE_SCOPE_BASE_CALLABLE_ID,
    ADD_VARIANT_CALLABLE_ID,
    ADD_VARIANT_BASE_CALLABLE_ID,
    EXTENDED_BY_CALLABLE_ID,
    EXTENDED_BY_BASE_CALLABLE_ID,
)

private val CSS_STYLE_BASE_CALLABLE_IDS = setOf(CSS_STYLE_BASE_EXTENSION_CALLABLE_ID, CSS_STYLE_SCOPE_BASE_CALLABLE_ID)

/**
 * Extract a [ClassId] from a type if this type represents one of Kobweb's singleton style objects.
 *
 * These are objects that are expected to be public and declared at the top level of a file (or inside a top-level
 * object) so that the generated `main` function can find and register them at startup.
 */
context(kaSession: KaSession)
val KaType.styleSheetSingletonClassId: ClassId? get() = with(kaSession) {
    expandedSymbol?.classId?.takeIf { it in STYLE_SINGLETON_CLASS_IDS }
}

context(kaSession: KaSession)
val KtCallExpression.styleSheetSingletonCallableId: CallableId? get() {
    return resolveToCallableId()?.takeIf { it in STYLE_SINGLETON_CALLABLE_IDS }
}

sealed interface StyleSheetBlock {
    companion object {
        // Check if this is the "base" in "CssStyle.base { ... }" or "CssStyle { base { ... } }"
        context(kaSession: KaSession)
        fun KtCallExpression.isBaseCall(): Boolean = with(kaSession) {
            val callee = calleeExpression as? KtNameReferenceExpression ?: return false
            val symbol = callee.mainReference.resolveToSymbol() as? KaCallableSymbol ?: return false
            return symbol.callableId in CSS_STYLE_BASE_CALLABLE_IDS
        }

        // Check for "CssStyle.base { ... }" format
        context(kaSession: KaSession)
        fun KtExpression.isCssStyleReceiver(): Boolean = with(kaSession) {
            val nameRef = this@isCssStyleReceiver as? KtNameReferenceExpression ?: return false
            val symbol = nameRef.mainReference.resolveToSymbol() as? KaClassSymbol ?: return false
            return symbol.classId == CSS_STYLE_COMPANION_CLASS_ID
        }

        // Check for "CssStyle { base { ... } }" format
        context(kaSession: KaSession)
        fun KtCallExpression.isCssStyleCall(): Boolean = with(kaSession) {
            val callee = calleeExpression as? KtNameReferenceExpression ?: return false
            val symbol = callee.mainReference.resolveToSymbol() as? KaCallableSymbol ?: return false
            return symbol.callableId == CSS_STYLE_CALLABLE_ID
        }

        context(kaSession: KaSession)
        fun containing(fromElement: PsiElement): StyleSheetBlock? = with(kaSession) {
            var current: PsiElement? = fromElement

            fun KtCallExpression.findExtraModifierArg(): KtValueArgument? = valueArgumentList?.arguments?.firstOrNull()

            while (current != null && current !is KtFile) {
                // Is this the "CssStyle.base { ... }" format?
                val dotExpr = current as? KtDotQualifiedExpression
                if (dotExpr != null && dotExpr.receiverExpression.isCssStyleReceiver()) {
                    val baseCall = (dotExpr.selectorExpression as? KtCallExpression)?.takeIf { it.isBaseCall() }
                    if (baseCall != null) {
                        return Style.Concise(
                            type = Style.Type.DEFINITION,
                            rootExpression = dotExpr,
                            baseCall = baseCall,
                            extraModifierArg = baseCall.findExtraModifierArg()
                        )
                    }
                }

                // Is this the "CssStyle { base { ... } }" format?
                val callExpr = current as? KtCallExpression
                if (callExpr != null && callExpr.isCssStyleCall()) {
                    val lambdaBody = callExpr.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression
                    if (lambdaBody != null) {
                        val topLevelCalls = lambdaBody.statements.filterIsInstance<KtCallExpression>()
                        val (baseCall, otherCalls) = topLevelCalls.partition { it.isBaseCall() }
                            .let { it.first.singleOrNull() to it.second }
                        if (baseCall != null) {
                            return Style.Relaxed(
                                type = Style.Type.DEFINITION,
                                rootExpression = callExpr,
                                extraModifierFunc = callExpr,
                                extraModifierArg = callExpr.findExtraModifierArg(),
                                baseCall = baseCall,
                                otherCalls = otherCalls,
                            )
                        }
                    }
                }

                current = current.parent
            }

            return null
        }
    }

    val rootExpression: PsiElement

    // Class which represents styles that gets associated with a class name, i.e., `CssStyle`, `CssStyleVariant`, or
    // `SomeStyle.extendedBy`
    sealed interface Style : StyleSheetBlock {
        companion object {
            context(kaSession: KaSession)
            fun containing(fromElement: PsiElement): Style? {
                return StyleSheetBlock.containing(fromElement)?.let { it as? Style }
            }
        }

        enum class Type {
            /**
             * e.g. `CssStyle { base { ... } }`
             */
            DEFINITION,

            /**
             * e.g. `SomeStyle.extendedBy { base { ... } }`
             */
            EXTENSION,

            /**
             * e.g. `SomeStyle.addVariant { base { ... } }`
             */
            VARIANT,
        }

        val type: Type
        val baseCall: KtCallExpression
        // The function that accepts the extraModifier argument. This may be the same as `baseCall` or `rootExpression`
        val extraModifierFunc: KtCallExpression
        val extraModifierArg: KtValueArgument?

        val bodyText: String? get() = baseCall.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression?.text

        class Concise(
            override val type: Type,
            override val rootExpression: PsiElement,
            override val baseCall: KtCallExpression,
            override val extraModifierArg: KtValueArgument?,
        ) : Style {
            override val extraModifierFunc: KtCallExpression = baseCall
        }

        class Relaxed(
            override val type: Type,
            override val rootExpression: PsiElement,
            override val baseCall: KtCallExpression,
            override val extraModifierFunc: KtCallExpression,
            override val extraModifierArg: KtValueArgument?,
            val otherCalls: List<KtCallExpression>,
        ) : Style
    }
}

