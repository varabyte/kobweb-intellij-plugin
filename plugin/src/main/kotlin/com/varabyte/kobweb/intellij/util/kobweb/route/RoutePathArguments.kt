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
 * A parameter that takes a route, e.g. `Link`'s `path`.
 *
 * @property type What the parameter accepts, from `@RoutePath(type = ...)`.
 * @property mustExist Whether the route should lead to a page (or another resource the site serves). False for
 *   parameters marked `@RoutePath(mustExist = false)`, e.g. ones that build links to pages that may not exist yet.
 */
class RoutePathParameter(val type: Type, val mustExist: Boolean) {
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
        to ("path" to RoutePathParameter.Type.URL),
    CallableId(FqName("com.varabyte.kobweb.navigation"), Name.identifier("Anchor"))
        to ("href" to RoutePathParameter.Type.URL),
    // Can also navigate to other sites
    CallableId(FqName("com.varabyte.kobweb.navigation"), FqName("Router"), Name.identifier("navigateTo"))
        to ("pathQueryAndFragment" to RoutePathParameter.Type.URL),
    CallableId(FqName("com.varabyte.kobweb.navigation"), FqName("Router"), Name.identifier("tryRoutingTo"))
        to ("pathQueryAndFragment" to RoutePathParameter.Type.PATH_QUERY_AND_FRAGMENT),
)

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
        fun findArgument(name: String) = routePath.arguments.firstOrNull { it.name.asString() == name }?.expression
        val type = (findArgument("type") as? KaAnnotationValue.EnumEntryValue)?.callableId?.callableName?.asString()
            ?.let { typeName -> RoutePathParameter.Type.entries.firstOrNull { it.name == typeName } }
            ?: RoutePathParameter.Type.URL
        val mustExist = (findArgument("mustExist") as? KaAnnotationValue.ConstantValue)?.value?.value as? Boolean
            ?: true
        return RoutePathParameter(type, mustExist)
    }

    val callableId = call.symbol.callableId ?: return null
    val (legacyName, legacyType) = LEGACY_ROUTE_PARAMETERS[callableId] ?: return null
    if (legacyName != parameter.name.asString()) return null
    return RoutePathParameter(legacyType, mustExist = true)
}

/**
 * Returns true if this expression is passed into a parameter that takes a route (see [findRoutePathParameter]).
 *
 * This method must be called inside an `analyze` block.
 */
context(kaSession: KaSession)
fun KtExpression.isRoutePathArgument(): Boolean = findRoutePathParameter() != null
