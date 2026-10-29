package com.varabyte.kobweb.intellij.util.kobweb.route

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * A parameter or property that holds some kind of path, e.g. one marked with `@RoutePath` or `@ApiPath`, and what that
 * tells us about strings used with it.
 *
 * Implementations add whatever else their annotation says, e.g. [RoutePathTarget.type].
 *
 * @property mustExist Whether the path should lead to something that exists, from the annotation's `mustExist`
 *   argument.
 */
interface PathTarget {
    val mustExist: Boolean
}

/**
 * A Kobweb annotation for parameters and properties that hold some kind of path, e.g. `@RoutePath` on `Link`'s `path`,
 * which tells us what strings used with them should look like.
 *
 * Older versions of Kobweb didn't have these annotations, so this also lists the declarations they were later added to.
 *
 * @param T What a string's target tells us about it, e.g. [RoutePathTarget].
 * @property classId The annotation class.
 * @property toPathTarget Returns what an instance of the annotation says, e.g. from its arguments.
 * @property legacyParameters Parameters that hold a path, by the function they belong to, as (parameter name, target)
 *   pairs.
 * @property legacyConstructorParameters Like [legacyParameters], but for constructors, by the class they belong to.
 * @property legacyGetters Property getters that return a path, i.e. what strings compared against them should look
 *   like.
 * @property legacySetters Property setters that take a path, i.e. what strings assigned to them should look like.
 */
class PathAnnotation<T : PathTarget>(
    val classId: ClassId,
    val toPathTarget: (KaAnnotation) -> T,
    val legacyParameters: Map<CallableId, Pair<String, T>> = emptyMap(),
    val legacyConstructorParameters: Map<ClassId, Pair<String, T>> = emptyMap(),
    val legacyGetters: Map<CallableId, T> = emptyMap(),
    val legacySetters: Map<CallableId, T> = emptyMap(),
)

/**
 * Returns the value of the argument with this name, if it was passed in explicitly.
 *
 * (Arguments left at their default value aren't listed.)
 */
fun KaAnnotation.findArgument(name: String): KaAnnotationValue? {
    return arguments.firstOrNull { it.name.asString() == name }?.expression
}

/**
 * Returns the value of the boolean argument with this name, or [default] if it wasn't passed in explicitly.
 */
fun KaAnnotation.readBooleanArgument(name: String, default: Boolean): Boolean {
    return (findArgument(name) as? KaAnnotationValue.ConstantValue)?.value?.value as? Boolean ?: default
}

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
 * Returns the declaration this string is the initial value of, if any, e.g. the `home` property for the `"/blog"` in
 * `val home = "/blog"`, or the default `path` parameter for the `"/blog"` in `fun NavItem(path: String = "/blog")`.
 */
private fun KtStringTemplateExpression.findInitializedDeclaration(): KtCallableDeclaration? {
    return when (val parent = parent) {
        is KtProperty -> parent.takeIf { it.initializer == this }
        is KtParameter -> parent.takeIf { it.defaultValue == this }
        else -> null
    }
}

/**
 * Returns true if this string is used in a way that could have a path target (see [findPathTarget]), i.e. it's passed
 * into a function, compared against something, assigned to something, or the initial value of something.
 *
 * This is a quick syntax check to do before [findPathTarget], which requires Kotlin analysis.
 */
fun KtStringTemplateExpression.mayHavePathTarget(): Boolean {
    return parent is KtValueArgument
        || findComparedExpression() != null
        || findAssignedExpression() != null
        || findInitializedDeclaration() != null
}

/**
 * Returns the name of what this string is used with, e.g. "get" for the `"hello"` in `window.api.get("hello")`, "route"
 * for the `"chat"` in `stream.route == "chat"`, or "home" for the `"/blog"` in `val home = "/blog"`.
 *
 * This only looks at syntax, so the name is just a hint at what the string's target (see [findPathTarget]) might be,
 * e.g. for a cheap check against [findTargetNames] before running analysis.
 */
fun KtStringTemplateExpression.findTargetName(): String? {
    fun KtExpression.referencedName(): String? {
        val reference = (this as? KtQualifiedExpression)?.selectorExpression ?: this
        return (reference as? KtNameReferenceExpression)?.getReferencedName()
    }

    if (parent is KtValueArgument) {
        val callElement = parent.getStrictParentOfType<KtCallElement>() ?: return null
        return callElement.calleeExpression?.referencedName()
    }
    return findComparedExpression()?.referencedName()
        ?: findAssignedExpression()?.referencedName()
        ?: findInitializedDeclaration()?.name
}

/**
 * Returns the names of the declarations that are targets of [annotation] (see [findTargetName]), i.e. those marked
 * with it in this project's indexed code, plus its legacy declarations.
 *
 * This is only as complete as the index, so it can miss annotated declarations that aren't in it (e.g. in a compiled
 * library without sources). It can also include ones that aren't really targets, as annotations are matched by name
 * only, and different declarations can share a name. So it's only good for quick checks that can afford to be wrong
 * now and then, e.g. whether to show a completion popup.
 */
fun Project.findTargetNames(annotation: PathAnnotation<*>): Set<String> = buildSet {
    annotation.legacyParameters.keys.forEach { callableId -> add(callableId.callableName.asString()) }
    annotation.legacyConstructorParameters.keys.forEach { classId -> add(classId.shortClassName.asString()) }
    (annotation.legacyGetters.keys + annotation.legacySetters.keys).forEach { callableId ->
        add(callableId.callableName.asString())
    }

    val annotationName = annotation.classId.shortClassName.asString()
    KotlinAnnotationsIndex[annotationName, this@findTargetNames, GlobalSearchScope.allScope(this@findTargetNames)]
        .forEach { entry ->
            when (val declaration = entry.getStrictParentOfType<KtModifierList>()?.parent) {
                is KtParameter -> {
                    // A parameter is a target for arguments passed to its function, and also for its own default value
                    // (and, for a constructor property, for comparisons and assignments)
                    declaration.name?.let(::add)
                    when (val function = declaration.ownerFunction) {
                        is KtPrimaryConstructor -> function.getContainingClassOrObject().name
                        is KtSecondaryConstructor -> function.getContainingClassOrObject().name
                        else -> function?.name
                    }?.let(::add)
                }

                is KtProperty -> declaration.name?.let(::add)
                is KtPropertyAccessor -> declaration.property.name?.let(::add)
            }
        }
}

/**
 * Returns the path target that this declaration's [annotation] describes, if it has one.
 */
// The session isn't used, but a `KaAnnotated` is only valid inside one anyway, so this documents that
@Suppress("UNUSED_PARAMETER")
context(_: KaSession)
private fun <T : PathTarget> KaAnnotated.findPathTarget(annotation: PathAnnotation<T>): T? {
    return annotations[annotation.classId].firstOrNull()?.let(annotation.toPathTarget)
}

/**
 * Returns the target of the parameter that this string is passed into, if that parameter is marked with
 * [annotation] (or is one of its legacy parameters).
 */
context(kaSession: KaSession)
private fun <T : PathTarget> KtStringTemplateExpression.findParameterPathTarget(annotation: PathAnnotation<T>): T? =
    with(kaSession) {
        val argument = parent as? KtValueArgument ?: return null
        val callElement = argument.getStrictParentOfType<KtCallElement>() ?: return null
        val call = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        val parameter = call.argumentMapping[this@findParameterPathTarget]?.symbol ?: return null
        parameter.findPathTarget(annotation)?.let { return it }

        val (legacyName, legacyTarget) = when (val function = call.symbol) {
            // Constructors don't have a callable ID of their own
            is KaConstructorSymbol -> function.containingClassId?.let { annotation.legacyConstructorParameters[it] }
            else -> function.callableId?.let { annotation.legacyParameters[it] }
        } ?: return null
        return legacyTarget.takeIf { legacyName == parameter.name.asString() }
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
 * Returns the target of the property that this string is compared against, if that property is marked with
 * [annotation] (or its getter is, which takes priority, as that's what describes the value being compared).
 */
context(_: KaSession)
private fun <T : PathTarget> KtStringTemplateExpression.findComparedPathTarget(annotation: PathAnnotation<T>): T? {
    val property = findComparedExpression()?.resolveToProperty() ?: return null
    return property.getter?.findPathTarget(annotation)
        ?: property.findPathTarget(annotation)
        ?: annotation.legacyGetters[property.callableId]
}

/**
 * Returns the target of the property that this string is assigned to, if that property is marked with [annotation]
 * (or its setter is, which takes priority, as that's what describes the value being assigned).
 */
context(_: KaSession)
private fun <T : PathTarget> KtStringTemplateExpression.findAssignedPathTarget(annotation: PathAnnotation<T>): T? {
    val property = findAssignedExpression()?.resolveToProperty() ?: return null
    return property.setter?.findPathTarget(annotation)
        ?: property.findPathTarget(annotation)
        ?: annotation.legacySetters[property.callableId]
}

/**
 * Returns the path target that describes this property's initial value, from its [annotation], if any.
 *
 * The setter's annotation takes priority, as it describes values written into the property (even though Kotlin
 * doesn't actually call the setter for an initial value), then the property's, then the getter's.
 */
context(_: KaSession)
private fun <T : PathTarget> KaPropertySymbol.findInitialValuePathTarget(annotation: PathAnnotation<T>): T? {
    return setter?.findPathTarget(annotation)
        ?: findPathTarget(annotation)
        ?: getter?.findPathTarget(annotation)
}

/**
 * Returns the target of the declaration that this string is the initial value of, if that declaration is marked with
 * [annotation], e.g. `@RoutePath val home = "/blog"` or `fun NavItem(@RoutePath path: String = "/blog")`.
 *
 * The default value of a constructor property (e.g. `class Nav(@property:RoutePath val path: String = "/blog")`) is
 * also the property's initial value, so its annotations count too.
 */
context(kaSession: KaSession)
private fun <T : PathTarget> KtStringTemplateExpression.findInitializedPathTarget(annotation: PathAnnotation<T>): T? =
    with(kaSession) {
        return when (val declaration = findInitializedDeclaration()) {
            is KtParameter -> {
                val parameter = declaration.symbol
                parameter.findPathTarget(annotation)
                    ?: (parameter as? KaValueParameterSymbol)?.generatedPrimaryConstructorProperty
                        ?.findInitialValuePathTarget(annotation)
            }

            is KtProperty -> (declaration.symbol as? KaPropertySymbol)?.findInitialValuePathTarget(annotation)
            else -> null
        }
    }

/**
 * Returns what this string is used with, if that's marked with [annotation], e.g. `Link`'s `path` parameter (marked
 * with `@RoutePath`) for the `"/blog"` in `Link("/blog")`.
 *
 * Call [mayHavePathTarget] first to skip strings that can't have one without running analysis.
 *
 * This method must be called inside an `analyze` block.
 */
context(_: KaSession)
fun <T : PathTarget> KtStringTemplateExpression.findPathTarget(annotation: PathAnnotation<T>): T? {
    return findParameterPathTarget(annotation)
        ?: findComparedPathTarget(annotation)
        ?: findAssignedPathTarget(annotation)
        ?: findInitializedPathTarget(annotation)
}
