package com.varabyte.kobweb.intellij.util.kobweb.route

import org.jetbrains.kotlin.analysis.api.KaSession
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
 * Returns true if this expression is passed into a parameter that takes a route, e.g. the `"/blog"` in
 * `Link("/blog")`.
 *
 * Such parameters are annotated with Kobweb's `@RoutePath` (which also lets users mark their own), although older
 * versions of Kobweb didn't have it, so we also recognize the parameters it was later added to.
 *
 * This method must be called inside an `analyze` block.
 */
context(kaSession: KaSession)
fun KtExpression.isRoutePathArgument(): Boolean = with(kaSession) {
    val argument = parent as? KtValueArgument ?: return false
    val callElement = argument.getStrictParentOfType<KtCallElement>() ?: return false
    val call = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return false
    val parameter = call.argumentMapping[this@isRoutePathArgument]?.symbol ?: return false

    if (ROUTE_PATH_CLASS_ID in parameter.annotations) return true
    val callableId = call.symbol.callableId ?: return false
    return LEGACY_ROUTE_PARAMETERS[callableId] == parameter.name.asString()
}
