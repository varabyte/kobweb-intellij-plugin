package com.varabyte.kobweb.intellij.quickfixes.route

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteParamKey
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteParamsScope
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Replace a lookup of a key that's always in a route's dynamic params with `dynamicParams.getValue`, e.g.
 * `params["post"]!!` or `dynamicParams.getOrElse("post") { "" }` with `dynamicParams.getValue("post")`.
 */
class ReplaceWithDynamicParamGetValueQuickFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.replace_with_dynamic_param_get_value.name")

    /** @param element The lookup, e.g. `map["key"]` or `map.get("key")`. */
    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val key = when (element) {
            is KtArrayAccessExpression -> element.indexExpressions.singleOrNull()
            is KtQualifiedExpression -> {
                val call = element.selectorExpression as? KtCallExpression
                call?.valueArguments?.firstOrNull()?.getArgumentExpression()
            }
            else -> null
        } as? KtStringTemplateExpression ?: return

        RouteParamKey.from(key)?.replaceWithDynamicParamsGetValue()
    }
}

/**
 * Replace this key's lookup with `dynamicParams.getValue`, e.g. `params["post"]!!` with
 * `dynamicParams.getValue("post")`. Only call this for a key that's always in the route's dynamic params.
 *
 * Null handling that follows the lookup (a `!!` or `?: default`) is removed, and so is the `?` of a local val's type
 * that the lookup initializes, as they'd be redundant.
 */
internal fun RouteParamKey.replaceWithDynamicParamsGetValue() {
    val (map, operator) = when (val access = access) {
        is KtArrayAccessExpression -> access.arrayExpression to "."
        is KtQualifiedExpression -> access.receiverExpression to access.operationSign.value
        else -> return
    }
    if (map == null) return

    // e.g. `ctx.route.params` into `ctx.route.dynamicParams`
    val dynamicParamsName = RouteParamsScope.DYNAMIC_ONLY.propertyName
    val dynamicParams = when (map) {
        is KtQualifiedExpression -> "${map.receiverExpression.text}${map.operationSign.value}$dynamicParamsName"
        else -> dynamicParamsName
    }
    val getValue = KtPsiFactory(expression.project)
        .createExpression("$dynamicParams${operator}getValue(${expression.text})")

    // Null handling would be redundant afterwards, e.g. a `!!` or the right side of an elvis operator
    val nullHandling = when (val parent = access.parent) {
        is KtPostfixExpression if parent.operationToken == KtTokens.EXCLEXCL -> parent
        is KtBinaryExpression if parent.operationToken == KtTokens.ELVIS && parent.left == access -> parent
        else -> null
    }
    val replaced = (nullHandling ?: access).replace(getValue)

    // e.g. `val post: String? = ...` into `val post: String = ...`, so the compiler can point out null checks that
    // are now redundant in the code using it. Only for local vals: a var might be set to null later, and other
    // properties' types are API.
    val property = replaced.parent as? KtProperty ?: return
    if (!property.isLocal || property.isVar || property.initializer != replaced) return
    val nullableType = property.typeReference?.typeElement as? KtNullableType ?: return
    val innerType = nullableType.innerType ?: return
    nullableType.replace(innerType)
}
