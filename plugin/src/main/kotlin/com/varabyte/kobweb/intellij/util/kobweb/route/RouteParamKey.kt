package com.varabyte.kobweb.intellij.util.kobweb.route

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

private val ROUTE_PARAMS_PROPERTIES: Map<CallableId, RouteParamsScope> = buildMap {
    val classIds = listOf(
        ClassId(FqName("com.varabyte.kobweb.core"), Name.identifier("RouteInfo")),
        ClassId(FqName("com.varabyte.kobweb.api.http"), Name.identifier("Request")),
    )
    for (classId in classIds) {
        for (scope in RouteParamsScope.entries) {
            put(CallableId(classId, Name.identifier(scope.propertyName)), scope)
        }
    }
}

/** The `Map` functions whose first argument is a key, besides the `get` operator (e.g. `params["post"]`). */
private val MAP_KEY_FUNCTIONS = setOf("get", "getValue", "getOrDefault", "getOrElse", "containsKey", "contains")

/** The `Map` functions that read the value of a key, which `getValue` can replace if the key is always present. */
private val MAP_VALUE_FUNCTIONS = setOf("get", "getValue", "getOrDefault", "getOrElse")

/**
 * A string used as a key into a map of route params, e.g. the `"post"` in `ctx.route.params["post"]` (on a page) or
 * `ctx.req.params.getValue("post")` (in an API endpoint).
 *
 * [from] only checks the shape of the code and the name of the map, which is quick, so call [findScope] to confirm it's
 * really a key into Kobweb's route params.
 *
 * @property expression The key itself.
 * @property access The map access the key is part of, e.g. `map["key"]`, `map.getValue("key")`, or `"key" in map`.
 * @property functionName The `Map` function the key is passed to, e.g. "getValue". This is "contains" for
 *   `"key" in map`, and null for `map["key"]`.
 * @property scopeFromName The [RouteParamsScope] that the map's name suggests. See also
 *   [RouteParamsScope.fromPropertyName]. Use [findScope] to resolve the real scope, which will either be this one or
 *   null (if the map turns out to be some other class's property that happens to have the same name). Therefore,
 *   it's safe to skip a key based on this alone, but confirm with [findScope] before reporting it.
 */
class RouteParamKey private constructor(
    val expression: KtStringTemplateExpression,
    val access: KtExpression,
    val functionName: String?,
    val scopeFromName: RouteParamsScope,
    private val mapName: KtNameReferenceExpression,
) {
    /** The key's text, or null if it has template expressions or escapes, so could be anything. */
    val value: String?
        get() = expression.entries
            .takeIf { entries -> entries.all { entry -> entry is KtLiteralStringTemplateEntry } }
            ?.joinToString("") { entry -> entry.text }

    /**
     * Returns the route params scope this is a key into, or null if the map isn't one of Kobweb's route params after all.
     *
     * [scopeFromName] is only a guess from the map's name, so use this to confirm it before reporting a problem to the
     * user.
     *
     * This method must be called inside an [analyze] block.
     */
    context(_: KaSession)
    fun findScope(): RouteParamsScope? = findMapCallableId()?.let(ROUTE_PARAMS_PROPERTIES::get)

    /**
     * Returns true if the map belongs to a class that also has a `dynamicParams` property, so this key could be read
     * from that instead, e.g. `ctx.route.dynamicParams` for `ctx.route.params["post"]`.
     *
     * Note that `Request` didn't have a `dynamicParams` property until recently, so we need to explicitly check for its
     * existence in case we are analyzing an older version of Kobweb.
     *
     * This method must be called inside an [analyze] block.
     */
    context(kaSession: KaSession)
    fun canSwitchToDynamicParams(): Boolean = with(kaSession) {
        val callableId = findMapCallableId() ?: return false
        // Only RouteInfo.params / Request.params (or their dynamicParams), not any class with a dynamicParams member
        if (callableId !in ROUTE_PARAMS_PROPERTIES) return false
        val classId = callableId.classId ?: return false
        val dynamicParamsName = Name.identifier(RouteParamsScope.DYNAMIC_ONLY.propertyName)
        return findClass(classId)?.memberScope?.callables(dynamicParamsName)?.any() == true
    }

    context(kaSession: KaSession)
    private fun findMapCallableId(): CallableId? = with(kaSession) {
        (mapName.mainReference.resolveToSymbol() as? KaPropertySymbol)?.callableId
    }

    companion object {
        /**
         * Returns [expression] as a key into a map of route params, or null if it isn't used as a map key or the map
         * isn't named like one of Kobweb's route params (e.g. `params` or `dynamicParams`).
         */
        fun from(expression: KtStringTemplateExpression): RouteParamKey? {
            when (val parent = expression.parent) {
                // e.g. `map["key"]`
                is KtContainerNode -> {
                    val arrayAccess = parent.parent as? KtArrayAccessExpression ?: return null
                    if (arrayAccess.indexExpressions.singleOrNull() != expression) return null
                    return create(expression, arrayAccess, arrayAccess.arrayExpression, functionName = null)
                }

                // e.g. `"key" in map` or `"key" !in map`
                is KtBinaryExpression -> {
                    if (parent.operationToken != KtTokens.IN_KEYWORD && parent.operationToken != KtTokens.NOT_IN) {
                        return null
                    }
                    if (parent.left != expression) return null
                    return create(expression, parent, parent.right, functionName = "contains")
                }

                // e.g. `map.getValue("key")`
                is KtValueArgument -> {
                    val call = parent.parent?.parent as? KtCallExpression ?: return null
                    if (call.valueArguments.firstOrNull() != parent) return null
                    val functionName = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
                    if (functionName !in MAP_KEY_FUNCTIONS) return null
                    val qualified = call.parent as? KtQualifiedExpression ?: return null
                    if (qualified.selectorExpression != call) return null
                    return create(expression, qualified, qualified.receiverExpression, functionName)
                }

                else -> return null
            }
        }

        private fun create(
            expression: KtStringTemplateExpression,
            access: KtExpression,
            map: KtExpression?,
            functionName: String?,
        ): RouteParamKey? {
            // e.g. `params` in `ctx.route.params`
            val mapName = ((map as? KtQualifiedExpression)?.selectorExpression ?: map) as? KtNameReferenceExpression
                ?: return null
            val scope = RouteParamsScope.fromPropertyName(mapName.getReferencedName()) ?: return null
            return RouteParamKey(expression, access, functionName, scope, mapName)
        }
    }
}

/** True if this key's value is read, i.e. `map["key"]` or e.g. `map.getValue("key")`, rather than just checked for. */
val RouteParamKey.isValueLookup: Boolean get() = functionName == null || functionName in MAP_VALUE_FUNCTIONS

/** True if this key is read with `getValue`, i.e. `map.getValue("key")`, which throws if the key is missing. */
val RouteParamKey.isReadWithGetValue: Boolean get() = functionName == "getValue"
