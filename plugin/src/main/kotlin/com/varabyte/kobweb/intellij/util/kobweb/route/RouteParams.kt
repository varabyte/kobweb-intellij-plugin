package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentsOfType
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiRoute
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedFunction

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
    DYNAMIC_ONLY("dynamicParams");

    companion object {
        /** Returns the scope whose map has this property name, e.g. [DYNAMIC_ONLY] for "dynamicParams", if any. */
        fun fromPropertyName(name: String): RouteParamsScope? = entries.find { scope -> scope.propertyName == name }
    }
}

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
