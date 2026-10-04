package com.varabyte.kobweb.intellij.util.kobweb.style

import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock.Style.*
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertLambdaToReferenceUtils.getCallReferencedName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

// This file contains utilities to help with Kobweb's top-level singleton properties that read style modifiers and put
// their values into the site's stylesheet.

private val SILK_STYLE_PACKAGE = FqName("com.varabyte.kobweb.silk.style")
private val SILK_STYLE_ANIMATION_PACKAGE = SILK_STYLE_PACKAGE.child(Name.identifier("animation"))
val CSS_STYLE_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("CssStyle"))
private val CSS_STYLE_COMPANION_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("CssStyle.Companion"))
private val CSS_STYLE_SCOPE_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("StyleScope"))
private val GENERAL_KIND_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("GeneralKind"))
private val COMPONENT_KIND_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("ComponentKind"))

private val CSS_STYLE_VARIANT_CLASS_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("CssStyleVariant"))
private val KEYFRAMES_CLASS_ID = ClassId(SILK_STYLE_ANIMATION_PACKAGE, Name.identifier("Keyframes"))

private val STYLE_SINGLETON_CLASS_IDS = setOf(CSS_STYLE_CLASS_ID, CSS_STYLE_VARIANT_CLASS_ID, KEYFRAMES_CLASS_ID)

private val CSS_STYLE_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("CssStyle"))
private val CSS_STYLE_SCOPE_BASE_CALLABLE_ID = CallableId(CSS_STYLE_SCOPE_CLASS_ID, Name.identifier("base"))
val CSS_STYLE_BASE_EXTENSION_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("base"))
val ADD_VARIANT_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("addVariant"))
val ADD_VARIANT_BASE_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("addVariantBase"))
val EXTENDED_BY_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("extendedBy"))
val EXTENDED_BY_BASE_CALLABLE_ID = CallableId(SILK_STYLE_PACKAGE, Name.identifier("extendedByBase"))

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

private class StyleBlockTopLevelCalls(
    val baseCall: KtCallExpression?,
    val otherCalls: List<KtCallExpression>
)


sealed interface StyleSheetBlock {
    companion object {
        context(_: KaSession)
        private fun tryConvert(element: PsiElement): StyleSheetBlock? {
            val dotExpr = element as? KtDotQualifiedExpression
            if (dotExpr != null) {
                Definition.Concise.tryConvert(dotExpr)?.let { return it }
                Extended.Concise.tryConvert(dotExpr)?.let { return it }
                Extended.Relaxed.tryConvert(dotExpr)?.let { return it }
                Variant.Relaxed.tryConvert(dotExpr)?.let { return it }
                Variant.Concise.tryConvert(dotExpr)?.let { return it }
            }

            val callExpr = element as? KtCallExpression
            if (callExpr != null) {
                Definition.Relaxed.tryConvert(callExpr)?.let { return it }
                Keyframes.tryConvert(callExpr)?.let { return it }
            }
            return null
        }

        context(kaSession: KaSession)
        fun containing(fromElement: PsiElement): StyleSheetBlock? = with(kaSession) {
            var current: PsiElement? = fromElement

            while (current != null && current !is KtFile) {
                tryConvert(current)?.let { return it }
                current = current.parent
            }
            return null
        }
    }

    val rootExpression: KtExpression

    class Keyframes(override val rootExpression: KtCallExpression) : StyleSheetBlock {
        companion object {
            /** Check for "Keyframes { ... }" */
            // Here, KtCallExpression is the constructor call for the "Keyframes" class
            context(kaSession: KaSession)
            private fun KtCallExpression.isKeyframesCall(): Boolean = with(kaSession) {
                val callee = calleeExpression as? KtNameReferenceExpression ?: return false
                val symbol = callee.mainReference.resolveToSymbol() as? KaConstructorSymbol ?: return false
                return symbol.containingClassId == KEYFRAMES_CLASS_ID
            }

            context(_: KaSession)
            internal fun tryConvert(callExpr: KtCallExpression): Keyframes? {
                return if (callExpr.isKeyframesCall()) {
                    Keyframes(callExpr)
                } else null
            }
        }
    }

    // Class which represents styles that gets associated with a class name, i.e., `CssStyle`, `CssStyleVariant`, or
    // `SomeStyle.extendedBy`
    sealed interface Style : StyleSheetBlock {
        companion object {
            private fun KtCallExpression.findExtraModifierArg(): KtValueArgument? = valueArgumentList?.arguments?.firstOrNull()

            /** Check if this is the "base" in "CssStyle.base { ... }" or "CssStyle { base { ... } }" */
            context(kaSession: KaSession)
            private fun KtCallExpression.isBaseCall(): Boolean = with(kaSession) {
                val callee = calleeExpression as? KtNameReferenceExpression ?: return false
                val symbol = callee.mainReference.resolveToSymbol() as? KaCallableSymbol ?: return false
                return symbol.callableId in CSS_STYLE_BASE_CALLABLE_IDS
            }

            context(_: KaSession)
            private fun KtCallExpression.findTopLevelCallsInsideLambda(): StyleBlockTopLevelCalls? {
                val lambdaBody = lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression ?: return null
                val topLevelCalls = lambdaBody.statements.filterIsInstance<KtCallExpression>()
                return topLevelCalls.partition { it.isBaseCall() }
                    .let {
                        StyleBlockTopLevelCalls(
                            baseCall = it.first.singleOrNull(),
                            otherCalls = it.second
                        )
                    }
            }

            /**
             * Checks if the receiver expression of the dot-qualified call is a `CssStyle<T>` against some `T` type.
             */
            context(kaSession: KaSession)
            private fun KtDotQualifiedExpression.hasCssStyleReceiver(typeClassId: ClassId): Boolean = with(kaSession) {
                val receiverType = receiverExpression.expressionType?.upperBoundIfFlexible() as? KaClassType ?: return false

                // Check if receiver's type or supertypes match CssStyle<T>
                val cssStyleType = receiverType.allSupertypes
                    .plus(receiverType)
                    .filterIsInstance<KaClassType>()
                    .firstOrNull { it.classId == CSS_STYLE_CLASS_ID } ?: return false

                // Extract type argument T from CssStyle<T> and check for a match
                val typeArgument = cssStyleType.typeArguments.firstOrNull()?.type as? KaClassType ?: return false
                return typeArgument.allSupertypes
                    .plus(typeArgument)
                    .filterIsInstance<KaClassType>()
                    .any { it.classId == typeClassId }
            }

            private fun KtDotQualifiedExpression.getRootName() = (receiverExpression as KtNameReferenceExpression).getReferencedName()
            private fun KtCallExpression.getRootName() = (getCallReferencedName()!!)

            context(_: KaSession)
            fun containing(fromElement: PsiElement): Style? {
                return StyleSheetBlock.containing(fromElement)?.let { it as? Style }
            }
        }

        val rootName: String
        val baseCall: KtCallExpression
        // The function that accepts the extraModifier argument. This may be the same as `baseCall` or `rootExpression`
        val extraModifierFunc: KtCallExpression
        val extraModifierArg: KtValueArgument?

        val bodyText: String? get() = baseCall.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression?.text

        /**
         * e.g. `CssStyle { base { ... } }`
         */
        sealed interface Definition : Style {
            class Concise(
                override val rootExpression: KtDotQualifiedExpression,
                override val baseCall: KtCallExpression,
                override val extraModifierArg: KtValueArgument?,
            ) : Definition {
                companion object {
                    /** Check for "CssStyle.base { ... }" format */
                    context(kaSession: KaSession)
                    private fun KtExpression.isCssStyleReceiver(): Boolean = with(kaSession) {
                        val nameRef = this@isCssStyleReceiver as? KtNameReferenceExpression ?: return false
                        val symbol = nameRef.mainReference.resolveToSymbol() as? KaClassSymbol ?: return false
                        return symbol.classId == CSS_STYLE_COMPANION_CLASS_ID
                    }

                    context(kaSession: KaSession)
                    internal fun tryConvert(dotExpr: KtDotQualifiedExpression): Concise? = with(kaSession) {
                        if (dotExpr.receiverExpression.isCssStyleReceiver()) {
                            val baseCall = (dotExpr.selectorExpression as? KtCallExpression)?.takeIf { it.isBaseCall() }
                            if (baseCall != null) {
                                return Concise(
                                    rootExpression = dotExpr,
                                    baseCall = baseCall,
                                    extraModifierArg = baseCall.findExtraModifierArg()
                                )
                            }
                        }
                        return null
                    }
                }

                override val rootName get() = rootExpression.getRootName()
                override val extraModifierFunc: KtCallExpression = baseCall
            }

            class Relaxed(
                override val rootExpression: KtCallExpression,
                override val baseCall: KtCallExpression,
                override val extraModifierFunc: KtCallExpression,
                override val extraModifierArg: KtValueArgument?,
                val otherCalls: List<KtCallExpression>,
            ) : Definition {
                companion object {
                    /** Check for "CssStyle { base { ... } }" format */
                    context(kaSession: KaSession)
                    private fun KtCallExpression.isCssStyleCall(): Boolean = with(kaSession) {
                        val callee = calleeExpression as? KtNameReferenceExpression ?: return false
                        val symbol = callee.mainReference.resolveToSymbol() as? KaCallableSymbol ?: return false
                        return symbol.callableId == CSS_STYLE_CALLABLE_ID
                    }

                    context(_: KaSession)
                    internal fun tryConvert(callExpr: KtCallExpression): Relaxed? {
                        if (callExpr.isCssStyleCall()) {
                            val lambdaBody =
                                callExpr.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression
                            if (lambdaBody != null) {
                                val topLevelCalls = lambdaBody.statements.filterIsInstance<KtCallExpression>()
                                val (baseCall, otherCalls) = topLevelCalls.partition { it.isBaseCall() }
                                    .let { it.first.singleOrNull() to it.second }
                                if (baseCall != null) {
                                    return Relaxed(
                                        rootExpression = callExpr,
                                        extraModifierFunc = callExpr,
                                        extraModifierArg = callExpr.findExtraModifierArg(),
                                        baseCall = baseCall,
                                        otherCalls = otherCalls,
                                    )
                                }
                            }
                        }
                        return null
                    }
                }

                override val rootName get() = rootExpression.getRootName()
            }
        }

        /**
         * e.g. `SomeStyle.extendedBy { base { ... } }`
         */
        sealed interface Extended : Style {
            companion object {
                context(kaSession: KaSession)
                private fun KtDotQualifiedExpression.hasGeneralCssStyleReceiver(): Boolean = with(kaSession) {
                    return hasCssStyleReceiver(GENERAL_KIND_CLASS_ID)
                }

                /** Helper function to check for "SomeStyle.extendedBy" and "SomeStyle.extendedByBase" calls. */
                context(kaSession: KaSession)
                private fun KtDotQualifiedExpression.getMatchingExtendedByCall(id: CallableId): KtCallExpression? {
                    if (!hasGeneralCssStyleReceiver()) return null

                    val callExpr = selectorExpression as? KtCallExpression ?: return null
                    val callee = callExpr.calleeExpression as? KtNameReferenceExpression ?: return null
                    val symbol = with(kaSession) {
                        callee.mainReference.resolveToSymbol() as? KaCallableSymbol ?: return null
                    }

                    return callExpr.takeIf { symbol.callableId == id }
                }
            }
            class Concise(
                override val rootExpression: KtDotQualifiedExpression,
                override val baseCall: KtCallExpression,
                override val extraModifierArg: KtValueArgument?,
            ) : Extended {
                companion object {
                    /** Check for "SomeStyle.extendedByBase { ... }" format */
                    context(_: KaSession)
                    private fun KtDotQualifiedExpression.getExtendedByBaseCall(): KtCallExpression? =
                        getMatchingExtendedByCall(EXTENDED_BY_BASE_CALLABLE_ID)

                    context(_: KaSession)
                    internal fun tryConvert(dotExpr: KtDotQualifiedExpression): Concise? {
                        dotExpr.getExtendedByBaseCall()?.let { addVariantBaseCall ->
                            return Concise(
                                rootExpression = dotExpr,
                                baseCall = addVariantBaseCall,
                                extraModifierArg = addVariantBaseCall.findExtraModifierArg(),
                            )
                        }
                        return null
                    }
                }

                override val rootName get() = rootExpression.getRootName()
                override val extraModifierFunc: KtCallExpression = baseCall
            }

            class Relaxed(
                override val rootExpression: KtDotQualifiedExpression,
                override val baseCall: KtCallExpression,
                override val extraModifierFunc: KtCallExpression,
                override val extraModifierArg: KtValueArgument?,
                val otherCalls: List<KtCallExpression>,
            ) : Extended {
                companion object {
                    /** Check for "SomeStyle.extendedBy { base { ... } }" format */
                    context(_: KaSession)
                    private fun KtDotQualifiedExpression.getExtendedByCall(): KtCallExpression? =
                        getMatchingExtendedByCall(EXTENDED_BY_CALLABLE_ID)

                    context(_: KaSession)
                    internal fun tryConvert(dotExpr: KtDotQualifiedExpression): Relaxed? {
                        dotExpr.getExtendedByCall()?.let { addVariantCall ->
                            addVariantCall.findTopLevelCallsInsideLambda()?.let { topLevelCalls ->
                                topLevelCalls.baseCall?.let { baseCall ->
                                    return Relaxed(
                                        rootExpression = dotExpr,
                                        extraModifierFunc = addVariantCall,
                                        extraModifierArg = addVariantCall.findExtraModifierArg(),
                                        baseCall = baseCall,
                                        otherCalls = topLevelCalls.otherCalls,
                                    )
                                }
                            }
                        }

                        return null
                    }
                }

                override val rootName get() = rootExpression.getRootName()
            }
        }

        /**
         * e.g. `SomeStyle.addVariant { base { ... } }`
         */
        sealed interface Variant : Style {
            companion object {
                context(kaSession: KaSession)
                private fun KtDotQualifiedExpression.hasComponentCssStyleReceiver(): Boolean = with(kaSession) {
                    return hasCssStyleReceiver(COMPONENT_KIND_CLASS_ID)
                }

                /** Helper function to check for "SomeStyle.addVariant" and "SomeStyle.addVariantBase" calls. */
                context(kaSession: KaSession)
                private fun KtDotQualifiedExpression.getMatchingAddVariantCall(id: CallableId): KtCallExpression? {
                    if (!hasComponentCssStyleReceiver()) return null

                    val callExpr = selectorExpression as? KtCallExpression ?: return null
                    val callee = callExpr.calleeExpression as? KtNameReferenceExpression ?: return null
                    val symbol = with(kaSession) {
                        callee.mainReference.resolveToSymbol() as? KaCallableSymbol ?: return null
                    }

                    return callExpr.takeIf { symbol.callableId == id }
                }
            }
            class Concise(
                override val rootExpression: KtDotQualifiedExpression,
                override val baseCall: KtCallExpression,
                override val extraModifierArg: KtValueArgument?,
            ) : Variant {
                companion object {
                    /** Check for "SomeStyle.addVariantBase { ... }" format */
                    context(_: KaSession)
                    private fun KtDotQualifiedExpression.getAddVariantBaseCall(): KtCallExpression? =
                        getMatchingAddVariantCall(ADD_VARIANT_BASE_CALLABLE_ID)

                    context(_: KaSession)
                    internal fun tryConvert(dotExpr: KtDotQualifiedExpression): Concise? {
                        // Is this the "SomeStyle.addVariantBase { ... } }" format?
                        dotExpr.getAddVariantBaseCall()?.let { addVariantBaseCall ->
                            return Concise(
                                rootExpression = dotExpr,
                                baseCall = addVariantBaseCall,
                                extraModifierArg = addVariantBaseCall.findExtraModifierArg(),
                            )
                        }
                        return null
                    }
                }

                override val rootName get() = rootExpression.getRootName()
                override val extraModifierFunc: KtCallExpression = baseCall
            }

            class Relaxed(
                override val rootExpression: KtDotQualifiedExpression,
                override val baseCall: KtCallExpression,
                override val extraModifierFunc: KtCallExpression,
                override val extraModifierArg: KtValueArgument?,
                val otherCalls: List<KtCallExpression>,
            ) : Variant {
                companion object {
                    /** Check for "SomeStyle.addVariant { base { ... } }" format */
                    context(_: KaSession)
                    private fun KtDotQualifiedExpression.getAddVariantCall(): KtCallExpression? =
                        getMatchingAddVariantCall(ADD_VARIANT_CALLABLE_ID)

                    context(_: KaSession)
                    internal fun tryConvert(dotExpr: KtDotQualifiedExpression): Relaxed? {
                        dotExpr.getAddVariantCall()?.let { addVariantCall ->
                            addVariantCall.findTopLevelCallsInsideLambda()?.let { topLevelCalls ->
                                topLevelCalls.baseCall?.let { baseCall ->
                                    return Relaxed(
                                        rootExpression = dotExpr,
                                        extraModifierFunc = addVariantCall,
                                        extraModifierArg = addVariantCall.findExtraModifierArg(),
                                        baseCall = baseCall,
                                        otherCalls = topLevelCalls.otherCalls,
                                    )
                                }
                            }
                        }
                        return null
                    }
                }

                override val rootName get() = rootExpression.getRootName()
            }
        }
    }
}

