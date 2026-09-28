package com.varabyte.kobweb.intellij.util.kobweb.modifier

import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.util.psi.resolvedType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

private val COMPOSE_UI_PACKAGE = FqName("com.varabyte.kobweb.compose.ui")
val MODIFIER_CLASS_ID = ClassId(COMPOSE_UI_PACKAGE, Name.identifier("Modifier"))
private val MODIFIER_COMPANION_CLASS_ID = ClassId(COMPOSE_UI_PACKAGE, Name.identifier("Modifier.Companion"))
private val ATTRS_MODIFIER_FQNAME = COMPOSE_UI_PACKAGE.child(Name.identifier("attrsModifier"))
private val STYLE_MODIFIER_FQNAME = COMPOSE_UI_PACKAGE.child(Name.identifier("styleModifier"))

private val STYLESCOPE_PROPERTY_FQNAME = FqName("org.jetbrains.compose.web.css.StyleScope.property")
private val ATTRSSCOPE_ATTR_FQNAME = FqName("org.jetbrains.compose.web.attributes.AttrsScope.attr")
private val NAMED_EVENT_LISTENER_CLASS_ID = ClassId.fromString("org/jetbrains/compose/web/internal/runtime/NamedEventListener")
private val EVENTS_LISTENER_REGISTER_FQNAME = FqName("org.jetbrains.compose.web.attributes.EventsListenerScope.registerEventListener")

enum class WebModifierType { ATTRS, STYLE, UNKNOWN }

sealed class WebName(private val name: String) {
    fun asString() = name

    sealed class Css(name: String) : WebName(name)
    class StyleProperty(name: String) : Css(name)

    sealed class Html(name: String) : WebName(name)
    class Attribute(name: String) : Html(name)
    class Event(name: String) : Html(name)
}

/**
 * Checks if this is the `Modifier` instance.
 *
 * e.g. `Modifier` in `Modifier.backgroundColor(...).id("...")`
 *
 * This function should be called inside an [analyze] block.
 */
context(kaSession: KaSession)
fun KtNameReferenceExpression.isModifierCompanion(): Boolean = with(kaSession) {
    this@isModifierCompanion.resolvedType == MODIFIER_COMPANION_CLASS_ID
}

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
        return receiverType.expandedSymbol?.classId == MODIFIER_CLASS_ID
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
        return returnType.expandedSymbol?.classId == MODIFIER_CLASS_ID
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

/**
 * Searches deeply to discover declared style property, attribute, or event names.
 *
 * This works by looking for any calls to `StyleScope.property`, `AttrsScope.attr`, or
 * `EventsListenerScope.registerEventListener`, and, if found, returning [WebName] instances that wrap their values.
 *
 * This method returns a list of names because some modifiers actually declare a collection of values, like
 * `Modifier.size(...)` sets both `width` and `height`.
 *
 * For example, if a user adds a method in their code like:
 *
 * ```
 * fun Modifier.newCssProperty(value: String) = styleModifier {
 *   property("new-css-property", value)
 * }
 * ```
 * then this method will surface a [WebName.StyleProperty] with the value "new-css-property".
 *
 * This method may be expensive to call, so be sure to abort early if possible, e.g., by calling
 * ```
 * analyze(function) { if (!function.isModifierChainingExtension()) return }
 * ```
 *
 * If you do not actually care about the actual style, attribute, or event names, consider using [getWebModifierType]
 * which is also expensive but probably a little less so.
 */
fun KtNamedFunction.getWebNames(maxDepth: Int = 10): List<WebName> {
    require(maxDepth >= 0) { "maxDepth must be non-negative: $maxDepth" }
    val results = mutableListOf<WebName>()

    findWebNamesRecursively(
        maxDepth = maxDepth,
        currentDepth = 0,
        visited = setOf(),
        results = results
    )

    return results.distinct()
}

private fun KtNamedFunction.findWebNamesRecursively(
    maxDepth: Int,
    currentDepth: Int,
    visited: Set<KtNamedFunction>,
    results: MutableList<WebName>
) {
    val targetFunction = (this.navigationElement as? KtNamedFunction) ?: this

    analyze(targetFunction) {
        val body = targetFunction.bodyExpression ?: targetFunction.bodyBlockExpression
        ?: return@analyze

        val calls = PsiTreeUtil.collectElementsOfType(body, KtCallExpression::class.java)

        for (call in calls) {
            val resolvedCall = call.resolveToCall()?.singleFunctionCallOrNull() ?: continue
            val symbol = resolvedCall.partiallyAppliedSymbol.signature.symbol
            val callFqName = symbol.callableId?.asSingleFqName()

            when (callFqName) {
                STYLESCOPE_PROPERTY_FQNAME -> {
                    extractFirstStringArgument(call)?.let { name ->
                        results.add(WebName.StyleProperty(name))
                    }
                    continue
                }
                ATTRSSCOPE_ATTR_FQNAME -> {
                    extractFirstStringArgument(call)?.let { name ->
                        results.add(WebName.Attribute(name))
                    }
                    continue
                }
                EVENTS_LISTENER_REGISTER_FQNAME -> {
                    extractEventNameFromRegistration(call)?.let { eventName ->
                        results.add(WebName.Event(eventName))
                    }
                    continue
                }
            }

            // Continue recursion into nested functions/helpers
            val innerFunction = symbol.psi as? KtNamedFunction ?: continue
            if (currentDepth < maxDepth && !visited.contains(innerFunction)) {
                innerFunction.findWebNamesRecursively(
                    maxDepth = maxDepth,
                    currentDepth = currentDepth + 1,
                    visited = visited + targetFunction,
                    results = results
                )
            }
        }
    }
}

// Useful for extracting `"x"` from `property("x", ...)` and `attr("x", ...)`
context(kaSession: KaSession)
private fun extractFirstStringArgument(call: KtCallExpression): String? = with(kaSession) {
    val firstValueArgument = call.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null

    val constantValue = firstValueArgument.evaluate()
    if (constantValue is KaConstantValue.StringValue) {
        return constantValue.value
    }

    return null
}

// Useful for extracting `"x"` from `registerEventListener(SomeEventListener("x", ...))`
context(kaSession: KaSession)
private fun extractEventNameFromRegistration(call: KtCallExpression): String? = with(kaSession) {
    // "registerEventListener" takes a "NamedEventListener". So we go to the function scope that "registerEventListener"
    // is being called in and search ALL function calls to see if any are constructor invocations of any class that
    // is of that type. We then make the assumption that the first argument passed into that constructor is going to be
    // the name of the event.
    //
    // In this way, we handle both formats we've seen in the wilkd:
    //
    // ```
    // registerEventListener(SomeEventListener("x", ...))
    // ```
    //
    // and
    //
    // ```
    // val listener = SomeEventListener("x", ...)
    // registerEventListener(listener)
    // ```

    val containingFunction = call.getParentOfType<KtNamedFunction>(strict = true) ?: return null
    val body = containingFunction.bodyExpression ?: containingFunction.bodyBlockExpression ?: return null

    val allCalls = PsiTreeUtil.collectElementsOfType(body, KtCallExpression::class.java)

    val namedListenerType = buildClassType(NAMED_EVENT_LISTENER_CLASS_ID)
    for (candidateCall in allCalls) {
        val resolvedCall = candidateCall.resolveToCall()?.singleFunctionCallOrNull() ?: continue
        val symbol = resolvedCall.partiallyAppliedSymbol.signature.symbol

        // Check if the constructed type / return type implements NamedEventListener
        val returnType = symbol.returnType
        if (returnType.isSubtypeOf(namedListenerType)) {
            val eventNameArg = candidateCall.valueArguments.firstOrNull()?.getArgumentExpression() ?: continue
            val constantValue = eventNameArg.evaluate()

            if (constantValue is KaConstantValue.StringValue) {
                return constantValue.value
            }
        }
    }

    return null
}