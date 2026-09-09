package com.varabyte.kobweb.intellij.util.kobweb.modifier

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

private val COMPOSE_UI_PACKAGE = FqName("com.varabyte.kobweb.compose.ui")
val MODIFIER_ID = ClassId(COMPOSE_UI_PACKAGE, Name.identifier("Modifier"))
private val ATTRS_MODIFIER_FQNAME = COMPOSE_UI_PACKAGE.child(Name.identifier("attrsModifier"))
private val STYLE_MODIFIER_FQNAME = COMPOSE_UI_PACKAGE.child(Name.identifier("styleModifier"))

enum class WebModifierType { ATTRS, STYLE, UNKNOWN }

/**
 * Checks if this function is an extension on Kobweb's `Modifier` interface.
 *
 * e.g. `fun Modifier.myModifier() = ...`
 *
 * This function should be called inside an [analyze] block.
 */
context(kaSession: KaSession)
fun KtNamedFunction.isModifierExtension(): Boolean {
    val receiverRef = receiverTypeReference ?: return false
    kaSession.apply {
        val receiverType = receiverRef.type
        return receiverType.expandedSymbol?.classId == MODIFIER_ID
    }
}

/**
 * Checks if this function returns Kobweb's `Modifier` interface.
 *
 * e.g. `fun generateModifier(): Modifier = Modifier...`
 *
 * This function should be called inside an [analyze] block.
 */
context(kaSession: KaSession)
fun KtNamedFunction.returnsModifier(): Boolean {
    kaSession.apply {
        return returnType.expandedSymbol?.classId == MODIFIER_ID
    }
}

/**
 * Checks if this function both extends and returns Kobweb's `Modifier` interface.
 *
 * This is very useful for detecting chaining style and attribute modifiers.
 *
 * e.g. `fun Modifier.someStyle(): Modifier = styleModifier { ... }`
 *
 * This function should be called inside an [analyze] block.
 */
context(_: KaSession)
fun KtNamedFunction.isModifierChainingExtension(): Boolean {
    return isModifierExtension() && returnsModifier()
}

/**
 * Determines whether this function ultimately delegates to `attrsModifier` or `styleModifier`.
 *
 * For example, if a user adds a method in their code like:
 *
 * ```
 * fun Modifier.newCssProperty(value: String) = styleModifier {
 *   property("new-css-property", value)
 * }
 * ```
 * then you can check later to see that `Modifier.newCssProperty()` is a style-type modifier.
 *
 * This method may be expensive to call, so be sure to abort early if possible, e.g., by calling
 * ```
 * analyze(function) { if (!function.isModifierChainingExtension()) return }
 * ```
 */
fun KtNamedFunction.getWebModifierType(maxDepth: Int = 3): WebModifierType {
    require(maxDepth >= 0) { "maxDepth must be non-negative: $maxDepth" }
    return findWebModifierTypeRecursively(
        maxDepth = maxDepth,
        currentDepth = 0,
        visited = setOf()
    )
}

private fun KtNamedFunction.findWebModifierTypeRecursively(
    maxDepth: Int,
    currentDepth: Int,
    visited: Set<KtNamedFunction>
): WebModifierType {
    val targetFunction = (this.navigationElement as? KtNamedFunction) ?: this

    return analyze(targetFunction) {
        val body = targetFunction.bodyExpression ?: targetFunction.bodyBlockExpression
        ?: return@analyze WebModifierType.UNKNOWN

        val calls = PsiTreeUtil.collectElementsOfType(body, KtCallExpression::class.java)
        val functionsToVisit = mutableListOf<KtNamedFunction>()

        for (call in calls) {
            val resolvedCall = call.resolveToCall()?.singleFunctionCallOrNull() ?: continue
            val symbol = resolvedCall.partiallyAppliedSymbol.signature.symbol
            val callFqName = symbol.callableId?.asSingleFqName() ?: continue

            when (callFqName) {
                ATTRS_MODIFIER_FQNAME -> return@analyze WebModifierType.ATTRS
                STYLE_MODIFIER_FQNAME -> return@analyze WebModifierType.STYLE
            }

            val innerFunction = symbol.psi as? KtNamedFunction ?: continue
            if (!visited.contains(innerFunction)
                && (innerFunction.isModifierExtension() || innerFunction.returnsModifier())
            ) {
                functionsToVisit.add(innerFunction)
            }
        }

        if (currentDepth < maxDepth && functionsToVisit.isNotEmpty()) {
            val nextVisited = visited + this@findWebModifierTypeRecursively
            for (nextFunction in functionsToVisit) {
                val nestedType = nextFunction.findWebModifierTypeRecursively(
                    maxDepth = maxDepth,
                    currentDepth = currentDepth + 1,
                    visited = nextVisited
                )

                if (nestedType != WebModifierType.UNKNOWN) {
                    return@analyze nestedType
                }
            }
        }

        WebModifierType.UNKNOWN
    }
}
