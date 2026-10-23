package com.varabyte.kobweb.intellij.util.kobweb.route

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Kobweb's annotation for parameters that take a route, e.g. `Link`'s `path`.
 */
private val ROUTE_PATH_CLASS_ID = ClassId.topLevel(FqName("com.varabyte.kobweb.core.RoutePath"))

/**
 * Hardcoded list of parameters that take a route in versions of Kobweb from before it annotated them with `@RoutePath`,
 * by the function they belong to.
 */
private val LEGACY_ROUTE_PARAMETERS = mapOf(
    CallableId(FqName("com.varabyte.kobweb.silk.components.navigation"), Name.identifier("Link")) to "path",
    CallableId(FqName("com.varabyte.kobweb.navigation"), Name.identifier("Anchor")) to "href",
    CallableId(FqName("com.varabyte.kobweb.navigation"), FqName("Router"), Name.identifier("navigateTo"))
        to "pathQueryAndFragment",
    CallableId(FqName("com.varabyte.kobweb.navigation"), FqName("Router"), Name.identifier("tryRoutingTo"))
        to "pathQueryAndFragment",
)

/**
 * A parameter that takes a route, e.g. `Link`'s `path`.
 *
 * @property mustExist Whether the route should lead to a page (or another resource the site serves). False for
 *   parameters marked `@RoutePath(mustExist = false)`, e.g. ones that build links to pages that may not exist yet.
 */
class RoutePathParameter(val mustExist: Boolean)

/**
 * Returns the parameter that this expression is passed into if that parameter takes a route, e.g. `Link`'s `path` for
 * the `"/blog"` in `Link("/blog")`.
 *
 * Such parameters are annotated with Kobweb's `@RoutePath` (which also lets users mark their own), although older
 * versions of Kobweb didn't have it, so we also recognize the parameters it was later added to.
 *
 * This method must be called inside an `analyze` block.
 */
context(kaSession: KaSession)
fun KtExpression.findRoutePathParameter(): RoutePathParameter? = with(kaSession) {
    val argument = parent as? KtValueArgument ?: return null
    val callElement = argument.getStrictParentOfType<KtCallElement>() ?: return null
    val call = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return null
    val parameter = call.argumentMapping[this@findRoutePathParameter]?.symbol ?: return null

    val routePath = parameter.annotations[ROUTE_PATH_CLASS_ID].firstOrNull()
    if (routePath != null) {
        // Arguments left at their default value aren't listed
        val mustExist = routePath.arguments
            .firstOrNull { argument -> argument.name.asString() == "mustExist" }
            ?.let { argument -> (argument.expression as? KaAnnotationValue.ConstantValue)?.value?.value as? Boolean }
            ?: true
        return RoutePathParameter(mustExist)
    }

    val callableId = call.symbol.callableId ?: return null
    if (LEGACY_ROUTE_PARAMETERS[callableId] != parameter.name.asString()) return null
    return RoutePathParameter(mustExist = true)
}

/**
 * Returns true if this expression is passed into a parameter that takes a route (see [findRoutePathParameter]).
 *
 * This method must be called inside an `analyze` block.
 */
context(kaSession: KaSession)
fun KtExpression.isRoutePathArgument(): Boolean = findRoutePathParameter() != null
