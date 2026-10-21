package com.varabyte.kobweb.intellij.util.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.daemon.common.trimQuotes
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtUserType

/**
 * Resolve an expression into a [ClassId] containing the type it evaluates to.
 *
 * This method must be called inside an [analyze] block.
 */
context(kaSession: KaSession)
val KtExpression.resolvedClassId: ClassId? get() = with(kaSession) {
    expressionType?.expandedSymbol?.classId
}

/** Given any element inside a [KtDotQualifiedExpression], return the first item in the list */
tailrec fun KtDotQualifiedExpression.getEntireDotQualifiedExpression(): KtDotQualifiedExpression {
    val parentExpr = parent as? KtDotQualifiedExpression
    return parentExpr?.getEntireDotQualifiedExpression() ?: this
}

/** Given any element inside a [KtDotQualifiedExpression], return the first item in the list */
fun PsiElement?.getEntireDotQualifiedExpression(): KtDotQualifiedExpression? {
    if (this == null) return null
    val ktDotExpr = PsiTreeUtil.getParentOfType(this, KtDotQualifiedExpression::class.java) ?: return null
    return ktDotExpr.getEntireDotQualifiedExpression()
}


/**
 * Returns the leading expression of a [KtDotQualifiedExpression] chain.
 *
 * For example, the expression `Modifier` in the chain `Modifier.a().b().c()`
 *
 * This works no matter which part of the dot qualified expression you call this method on.
 */
tailrec fun KtDotQualifiedExpression.getRootReceiverExpression(): KtExpression {
    return (this.receiverExpression as? KtDotQualifiedExpression)?.getRootReceiverExpression() ?: this.receiverExpression
}

/**
 * Returns true if the function is tagged with any one of the given annotations.
 *
 * This method must be called inside an [analyze] block.
 */
context(kaSession: KaSession)
fun KtDeclaration.hasAnyAnnotation(vararg classIds: ClassId): Boolean = with(kaSession) {
    // Note: Don't cache this result with `CachedValuesManager`. A cached value keeps the code that computes it, which
    // would keep the analysis session it was first called with, long after that session is invalid.
    // Code adapted from https://kotlin.github.io/analysis-api/migrating-from-k1.html#using-analysis-api
    val annotations = this@hasAnyAnnotation.symbol.annotations
    classIds.any { it in annotations }
}

// Used to live in `org.jetbrains.kotlin.js.translate.declaration.hasCustomGetter` but IJ removed it in 251.*
fun KtProperty.hasCustomGetter() = getter?.hasBody() ?: false

/**
 * Check if this annotatable declaration is annotated with a target and (optional) text value.
 *
 * This method must be called inside an [analyze] block.
 */
context(kaSession: KaSession)
fun KtAnnotated.isAnnotatedWith(classId: ClassId, expectedValue: String? = null): Boolean = with(kaSession) {
    fun KaAnnotationValue?.flattenStringValues(): List<String> = when (this) {
        is KaAnnotationValue.ConstantValue -> listOfNotNull(value.render().trimQuotes())
        is KaAnnotationValue.ArrayValue -> values.flatMap { it.flattenStringValues() }
        else -> emptyList()
    }

    val selfDeclaration = this@isAnnotatedWith as? KtDeclaration ?: return false
    selfDeclaration.symbol.annotations.any { annotation ->
        if (annotation.classId != classId) return@any false
        if (expectedValue == null) return@any true
        annotation.arguments.any { namedValue ->
            val annotationValues = namedValue.expression.flattenStringValues()
            expectedValue in annotationValues
        }
    }
}

private val KOTLIN_SUPPRESS_CLASS_ID = ClassId.topLevel(FqName("kotlin.Suppress"))

/**
 * Returns true if this declaration is annotated with `@Suppress(suppressKey)`.
 *
 * This method must be called inside an [analyze] block.
 */
context(_: KaSession)
fun KtAnnotated.isSuppressedWith(suppressKey: String): Boolean {
    return isAnnotatedWith(KOTLIN_SUPPRESS_CLASS_ID, suppressKey)
}

/**
 * Resolve a call expression into a [CallableId], which you can use to test for fqn's representing methods.
 *
 * This could return null if, for example, the call expression is actually a lambda property (and not actually a real
 * method), or it is an invoke operator.
 *
 * This method must be called inside an [analyze] block.
 */
context(kaSession: KaSession)
fun KtCallExpression.resolveToCallableId(): CallableId? = with(kaSession) {
    val functionCall = resolveToCall()?.singleFunctionCallOrNull() ?: return@with null
    val symbol = functionCall.symbol
    return symbol.callableId
}

/**
 * Convert a call expression (which is text that is function-shaped) to an actual backing function.
 */
context(kaSession: KaSession)
fun KtCallExpression.resolveToKtNamedFunction(): KtNamedFunction? = with(kaSession) {
    val refExpr = calleeExpression as? KtNameReferenceExpression ?: return null
    val symbol = refExpr.mainReference.resolveToSymbol() as? KaNamedFunctionSymbol
    symbol?.psi as? KtNamedFunction
}

/**
 * Returns true if [name], written unqualified in this file, refers to the declaration with the fully qualified name
 * [fqn], either because it lives in this file's package or because this file imports it (possibly via an alias).
 *
 * This only looks at the PSI (the file's package and imports), so it is cheap and safe to call in places where Kotlin
 * analysis isn't allowed. However, it can be fooled by local declarations that shadow an import.
 */
fun KtFile.isInScopeAs(fqn: FqName, name: String): Boolean {
    val shortName = fqn.shortName().asString()
    if (packageFqName == fqn.parent() && name == shortName) return true
    return importDirectives.any { import ->
        val importedFqName = import.importedFqName ?: return@any false
        when {
            import.isAllUnder -> importedFqName == fqn.parent() && name == shortName
            else -> importedFqName == fqn && name == (import.aliasName ?: shortName)
        }
    }
}

/**
 * Returns true if this type, as written, refers to the declaration with the fully qualified name [fqn].
 *
 * Both qualified (`a.b.C`) and unqualified (`C`) references are supported, the latter via [KtFile.isInScopeAs], which
 * means this only looks at the PSI and has the same limitations.
 */
fun KtUserType.refersTo(fqn: FqName): Boolean {
    val name = referencedName ?: return false
    val qualifier = qualifier ?: return containingKtFile.isInScopeAs(fqn, name)
    return name == fqn.shortName().asString() && qualifier.text.filterNot { it.isWhitespace() } == fqn.parent().asString()
}

/**
 * Returns true if this annotation entry refers to the annotation class with the given fully qualified name.
 *
 * Unlike [isAnnotatedWith], this only looks at the PSI (see [KtUserType.refersTo]), so it is cheap and safe to call in
 * places where Kotlin analysis isn't allowed, e.g. on the UI thread or when providing references.
 */
fun KtAnnotationEntry.isAnnotationOfType(fqn: FqName): Boolean {
    return (typeReference?.typeElement as? KtUserType)?.refersTo(fqn) ?: false
}
