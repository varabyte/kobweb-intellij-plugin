package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentsOfType
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiRoute
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

private val INIT_ROUTE_FQN = FqName("com.varabyte.kobweb.core.init.InitRoute")

/**
 * Which values a map of route params holds, as a property of a page's `RouteInfo` or an API endpoint's `Request`.
 *
 * @property propertyName The name of the property that holds the map, e.g. "params".
 */
enum class RouteParamsScope(val propertyName: String) {
    /** Holds both dynamic and query parameters, meaning any key might be found in it at runtime. */
    QUERY_AND_DYNAMIC("params"),

    /** Only holds the values of dynamic segments, which means the full set of options will be known to us. */
    DYNAMIC_ONLY("dynamicParams"),
}

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
private val MAP_KEY_FUNCTIONS = setOf("get", "getValue", "getOrDefault", "getOrElse", "containsKey")

/**
 * Returns the map access this string is the key of, i.e. `map["key"]`, or `map.fn("key", ...)` for a function in
 * [functionNames], judging by the code's shape only.
 */
private fun KtStringTemplateExpression.findMapAccess(functionNames: Set<String>): KtExpression? {
    val parent = parent
    if (parent is KtContainerNode) {
        val arrayAccess = parent.parent as? KtArrayAccessExpression ?: return null
        return arrayAccess.takeIf { arrayAccess.indexExpressions.singleOrNull() == this }
    }

    val argument = parent as? KtValueArgument ?: return null
    val call = argument.parent?.parent as? KtCallExpression ?: return null
    if (call.valueArguments.firstOrNull() != argument) return null
    if ((call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() !in functionNames) return null
    val qualified = call.parent as? KtQualifiedExpression ?: return null
    return qualified.takeIf { qualified.selectorExpression == call }
}

/**
 * Returns the map this string is used as a key of, e.g. `ctx.route.params` for the `"post"` in
 * `ctx.route.params["post"]` or `ctx.route.params.getValue("post")`, judging by the code's shape only.
 */
private fun KtStringTemplateExpression.findMapForKey(): KtExpression? {
    return when (val access = findMapAccess(MAP_KEY_FUNCTIONS)) {
        is KtArrayAccessExpression -> access.arrayExpression
        is KtQualifiedExpression -> access.receiverExpression
        else -> null
    }
}

/** The `Map` functions that read the value of a key, which `getValue` can replace if the key is always present. */
private val MAP_VALUE_FUNCTIONS = setOf("get", "getValue", "getOrDefault", "getOrElse")

/**
 * Returns the expression that reads this key's value, i.e. `map["key"]`, or `map.fn("key", ...)` for `get`,
 * `getValue`, `getOrDefault`, or `getOrElse`, judging by the code's shape only.
 */
fun KtStringTemplateExpression.findValueLookup(): KtExpression? = findMapAccess(MAP_VALUE_FUNCTIONS)

private val KtExpression.referenceName: KtNameReferenceExpression?
    get() = ((this as? KtQualifiedExpression)?.selectorExpression ?: this) as? KtNameReferenceExpression

/**
 * Returns true if this string might be a key into route params, judging only by the name of the map it's used with,
 * e.g. the `"post"` in `ctx.route.params["post"]` (on a page) or `ctx.req.params["post"]` (in an API endpoint).
 *
 * This is a quick check that doesn't require Kotlin analysis (unlike [isRouteParamKey] or [findRouteParamsScope], which
 * give the real answer).
 *
 * @param scope If set, only keys into the map of this scope count.
 */
fun KtStringTemplateExpression.mayBeRouteParamKey(scope: RouteParamsScope? = null): Boolean {
    val name = findMapForKey()?.referenceName?.getReferencedName() ?: return false
    val scopes = scope?.let { listOf(it) } ?: RouteParamsScope.entries
    return scopes.any { it.propertyName == name }
}

/**
 * Returns the route params scope this string is a key into, if any, e.g. [RouteParamsScope.QUERY_AND_DYNAMIC] would be
 * returned for `"post"` in `ctx.route.params["post"]` (on a page) or `ctx.req.params["post"]` (in an API endpoint).
 *
 * Call [mayBeRouteParamKey] first to skip strings that can't be one without running analysis.
 *
 * This method must be called inside an [analyze] block.
 */
context(kaSession: KaSession)
fun KtStringTemplateExpression.findRouteParamsScope(): RouteParamsScope? = with(kaSession) {
    val name = findMapForKey()?.referenceName ?: return null
    val property = name.mainReference.resolveToSymbol() as? KaPropertySymbol ?: return null
    return ROUTE_PARAMS_PROPERTIES[property.callableId]
}

/**
 * Returns true if this key's route params map belongs to a class that also has a `dynamicParams` property, so the key
 * could be read from that instead, e.g. `ctx.route.dynamicParams` for `ctx.route.params["post"]`.
 *
 * Note that `Request` didn't have a `dynamicParams` property until recently, so we need to explicitly check for its
 * existence in case we are analyzing an older version of Kobweb.
 *
 * This method must be called inside an [analyze] block.
 */
context(kaSession: KaSession)
fun KtStringTemplateExpression.canSwitchToDynamicParams(): Boolean = with(kaSession) {
    val name = findMapForKey()?.referenceName ?: return false
    val property = name.mainReference.resolveToSymbol() as? KaPropertySymbol ?: return false
    val callableId = property.callableId ?: return false
    // Only RouteInfo.params / Request.params (or their dynamicParams), not any class with a dynamicParams member
    if (callableId !in ROUTE_PARAMS_PROPERTIES) return false
    val classId = callableId.classId ?: return false
    val dynamicParamsName = Name.identifier(RouteParamsScope.DYNAMIC_ONLY.propertyName)
    return findClass(classId)?.memberScope?.callables(dynamicParamsName)?.any() == true
}

/**
 * Returns true if this string is a key into route params, e.g. the `"post"` in `ctx.route.params["post"]` (on a page)
 * or `ctx.req.params["post"]` (in an API endpoint).
 *
 * Call [mayBeRouteParamKey] first to skip strings that can't be one without running analysis.
 *
 * This method must be called inside an [analyze] block.
 */
context(_: KaSession)
fun KtStringTemplateExpression.isRouteParamKey(): Boolean = findRouteParamsScope() != null

/**
 * Returns the route that route params read here belong to: that of the enclosing `@Page` or `@Api` function, or of the
 * page declared next to an enclosing `@InitRoute` function (which Kobweb requires to live in the same file).
 *
 * Other code (e.g. a layout, or a helper function that a page calls) might run for any route, so it has none.
 */
fun PsiElement.findRouteParamsRoute(): Route? {
    val function = parentsOfType<KtNamedFunction>().lastOrNull()?.takeIf { it.isTopLevel } ?: return null
    function.findRoute()?.let { return it }
    function.findApiRoute()?.let { return it }

    if (function.annotationEntries.none { it.isAnnotationOfType(INIT_ROUTE_FQN) }) return null
    return function.containingKtFile.declarations
        .mapNotNull { declaration -> (declaration as? KtNamedFunction)?.findRoute() }
        .singleOrNull()
}
