package com.varabyte.kobweb.intellij.util.kobweb.route

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

private val KOBWEB_CORE_PACKAGE = FqName("com.varabyte.kobweb.core")
private val KOBWEB_NAVIGATION_PACKAGE = FqName("com.varabyte.kobweb.navigation")

/**
 * Kobweb's annotation for parameters and properties that hold a route, e.g. `Link`'s `path`.
 */
private val ROUTE_PATH_CLASS_ID = ClassId(KOBWEB_CORE_PACKAGE, Name.identifier("RoutePath"))

/**
 * Something that a route string is used with, which holds a route, e.g. `Link`'s `path` parameter for the `"/blog"` in
 * `Link("/blog")`, or the `ctx.route.path` property for the `"/blog"` in `ctx.route.path == "/blog"`, or the `path`
 * property for the `"/blog"` in `path = "/blog"`.
 *
 * @property type What the target accepts, from `@RoutePath(type = ...)`.
 * @property mustExist Whether the route should lead to a page (or another resource the site serves). False for targets
 *   marked `@RoutePath(mustExist = false)`, e.g. parameters that build links to pages that may not exist yet.
 */
class RoutePathTarget(val type: Type, val mustExist: Boolean) {
    /**
     * What sort of route format is accepted.
     *
     * Mirrors `RoutePathType` in Kobweb, whose entries have the same names.
     */
    enum class Type {
        /** Any URL, including ones to other sites (e.g. "https://example.com"). */
        URL,

        /** A path, optionally followed by query parameters and a fragment, e.g. "/blog/post?sort=new#comments". */
        PATH_QUERY_AND_FRAGMENT,

        /** Just a path, e.g. "/blog/post". */
        PATH_ONLY,
    }
}

/**
 * Hardcoded list of parameters that take a route in versions of Kobweb from before it annotated them with `@RoutePath`,
 * by the function they belong to.
 */
private val LEGACY_ROUTE_PARAMETERS = mapOf(
    CallableId(FqName("com.varabyte.kobweb.silk.components.navigation"), Name.identifier("Link"))
        to ("path" to RoutePathTarget.Type.URL),
    CallableId(KOBWEB_NAVIGATION_PACKAGE, Name.identifier("Anchor"))
        to ("href" to RoutePathTarget.Type.URL),
    // Can also navigate to other sites
    CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("Router"), Name.identifier("navigateTo"))
        to ("pathQueryAndFragment" to RoutePathTarget.Type.URL),
    CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("Router"), Name.identifier("tryRoutingTo"))
        to ("pathQueryAndFragment" to RoutePathTarget.Type.PATH_QUERY_AND_FRAGMENT),
)

/**
 * Hardcoded list of property getters that return a route in versions of Kobweb from before it annotated them with
 * `@RoutePath`, i.e. what strings compared against them should look like.
 *
 * Kobweb marks these with `mustExist = false`, as they hold routes that might not lead to any page (e.g. the current
 * route, which might be a 404).
 */
private val LEGACY_ROUTE_GETTERS = buildMap {
    // `RouteInfo` was nested inside `PageContext` in older versions of Kobweb
    for (routeInfo in listOf(FqName("RouteInfo"), FqName("PageContext.RouteInfo"))) {
        put(
            CallableId(KOBWEB_CORE_PACKAGE, routeInfo, Name.identifier("path")),
            RoutePathTarget(RoutePathTarget.Type.PATH_ONLY, mustExist = false),
        )
        put(
            CallableId(KOBWEB_CORE_PACKAGE, routeInfo, Name.identifier("pathQueryAndFragment")),
            RoutePathTarget(RoutePathTarget.Type.PATH_QUERY_AND_FRAGMENT, mustExist = false),
        )
    }
    put(
        CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("RouteInterceptorScope"), Name.identifier("path")),
        RoutePathTarget(RoutePathTarget.Type.PATH_ONLY, mustExist = false),
    )
    put(
        CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("RouteInterceptorScope"), Name.identifier("pathQueryAndFragment")),
        RoutePathTarget(RoutePathTarget.Type.PATH_QUERY_AND_FRAGMENT, mustExist = false),
    )
}

/**
 * Hardcoded list of property setters that take a route in versions of Kobweb from before it annotated them with
 * `@RoutePath`, i.e. what strings assigned to them should look like.
 */
private val LEGACY_ROUTE_SETTERS = mapOf(
    // Route interceptors redirect to the path they set, so it should lead somewhere
    CallableId(KOBWEB_NAVIGATION_PACKAGE, FqName("RouteInterceptorScope"), Name.identifier("path"))
        to RoutePathTarget(RoutePathTarget.Type.PATH_ONLY, mustExist = true),
)

/**
 * Returns the expression this string is compared against, if any, e.g. `ctx.route.path` for the `"/blog"` in
 * `ctx.route.path == "/blog"` or in `when (ctx.route.path) { "/blog" -> ... }`.
 */
private fun KtStringTemplateExpression.findComparedExpression(): KtExpression? {
    return when (val parent = parent) {
        is KtBinaryExpression -> {
            if (parent.operationToken != KtTokens.EQEQ && parent.operationToken != KtTokens.EXCLEQ) return null
            if (parent.left == this) parent.right else parent.left
        }

        is KtWhenConditionWithExpression -> {
            val whenExpr = parent.getStrictParentOfType<KtWhenExpression>() ?: return null
            whenExpr.subjectVariable?.initializer ?: whenExpr.subjectExpression
        }

        else -> null
    }
}

/**
 * Returns the expression this string is assigned to, if any, e.g. `path` for the `"/blog"` in `path = "/blog"`.
 */
private fun KtStringTemplateExpression.findAssignedExpression(): KtExpression? {
    val assignment = parent as? KtBinaryExpression ?: return null
    if (assignment.operationToken != KtTokens.EQ || assignment.right != this) return null
    return assignment.left
}

/**
 * Returns true if this string is used in a way that could have a [RoutePathTarget], i.e. it's passed into a function,
 * compared against something, or assigned to something.
 *
 * This is a quick syntax check to do before [findRoutePathTarget], which requires Kotlin analysis.
 *
 * See also: [hasRoutePathTarget], the complete (but more expensive) check.
 */
fun KtStringTemplateExpression.mayHaveRoutePathTarget(): Boolean {
    return parent is KtValueArgument || findComparedExpression() != null || findAssignedExpression() != null
}

/**
 * Reads this declaration's `@RoutePath` annotation, if it has one.
 */
// The session isn't used, but a `KaAnnotated` is only valid inside one anyway, so this documents that
@Suppress("UNUSED_PARAMETER")
context(_: KaSession)
private fun KaAnnotated.readRoutePathAnnotation(): RoutePathTarget? {
    val routePath = annotations[ROUTE_PATH_CLASS_ID].firstOrNull() ?: return null
    // Arguments left at their default value aren't listed
    fun findArgument(name: String) = routePath.arguments.firstOrNull { it.name.asString() == name }?.expression
    val type = (findArgument("type") as? KaAnnotationValue.EnumEntryValue)?.callableId?.callableName?.asString()
        ?.let { typeName -> RoutePathTarget.Type.entries.firstOrNull { it.name == typeName } }
        ?: RoutePathTarget.Type.URL
    val mustExist = (findArgument("mustExist") as? KaAnnotationValue.ConstantValue)?.value?.value as? Boolean
        ?: true
    return RoutePathTarget(type, mustExist)
}

/**
 * Returns the parameter that this string is passed into if that parameter takes a route.
 *
 * Such parameters are annotated with Kobweb's `@RoutePath` (which also lets users mark their own), although older
 * versions of Kobweb didn't have it, so we also recognize the parameters it was later added to.
 */
context(kaSession: KaSession)
private fun KtStringTemplateExpression.findRoutePathParameter(): RoutePathTarget? = with(kaSession) {
    val argument = parent as? KtValueArgument ?: return null
    val callElement = argument.getStrictParentOfType<KtCallElement>() ?: return null
    val call = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return null
    val parameter = call.argumentMapping[this@findRoutePathParameter]?.symbol ?: return null
    parameter.readRoutePathAnnotation()?.let { return it }

    val callableId = call.symbol.callableId ?: return null
    val (legacyName, legacyType) = LEGACY_ROUTE_PARAMETERS[callableId] ?: return null
    if (legacyName != parameter.name.asString()) return null
    return RoutePathTarget(legacyType, mustExist = true)
}

/**
 * Returns the property this expression refers to, if any, e.g. `path` for `ctx.route.path`.
 */
context(kaSession: KaSession)
private fun KtExpression.resolveToProperty(): KaPropertySymbol? = with(kaSession) {
    val reference = (this@resolveToProperty as? KtQualifiedExpression)?.selectorExpression ?: this@resolveToProperty
    return (reference as? KtNameReferenceExpression)?.mainReference?.resolveToSymbol() as? KaPropertySymbol
}

/**
 * Returns the property that this string is compared against if that property holds a route, i.e. it's annotated with
 * Kobweb's `@RoutePath` (or its getter is, which takes priority, as that's what describes the value being compared).
 *
 * Older versions of Kobweb didn't have the annotation, so we also recognize the properties it was later added to, e.g.
 * `ctx.route.path`.
 */
context(_: KaSession)
private fun KtStringTemplateExpression.findComparedRoutePathProperty(): RoutePathTarget? {
    val property = findComparedExpression()?.resolveToProperty() ?: return null
    return property.getter?.readRoutePathAnnotation()
        ?: property.readRoutePathAnnotation()
        ?: LEGACY_ROUTE_GETTERS[property.callableId]
}

/**
 * Returns the property that this string is assigned to if that property takes a route, i.e. it's annotated with
 * Kobweb's `@RoutePath` (or its setter is, which takes priority, as that's what describes the value being assigned).
 *
 * Older versions of Kobweb didn't have the annotation, so we also recognize the properties it was later added to, e.g.
 * `RouteInterceptorScope.path`.
 */
context(_: KaSession)
private fun KtStringTemplateExpression.findAssignedRoutePathProperty(): RoutePathTarget? {
    val property = findAssignedExpression()?.resolveToProperty() ?: return null
    return property.setter?.readRoutePathAnnotation()
        ?: property.readRoutePathAnnotation()
        ?: LEGACY_ROUTE_SETTERS[property.callableId]
}

/**
 * Returns what this string is used with if that holds a route (see [RoutePathTarget]), e.g. `Link`'s `path` parameter
 * for the `"/blog"` in `Link("/blog")`.
 *
 * Call [mayHaveRoutePathTarget] first to skip strings that can't have one without running analysis.
 *
 * This method must be called inside an `analyze` block.
 */
context(_: KaSession)
fun KtStringTemplateExpression.findRoutePathTarget(): RoutePathTarget? {
    return findRoutePathParameter() ?: findComparedRoutePathProperty() ?: findAssignedRoutePathProperty()
}

/**
 * Returns true if this string is used with something that holds a route (see [findRoutePathTarget]).
 *
 * This method must be called inside an `analyze` block.
 */
context(_: KaSession)
fun KtStringTemplateExpression.hasRoutePathTarget(): Boolean = findRoutePathTarget() != null
