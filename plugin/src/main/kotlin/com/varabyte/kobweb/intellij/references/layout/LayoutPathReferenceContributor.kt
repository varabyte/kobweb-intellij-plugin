package com.varabyte.kobweb.intellij.references.layout

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.LAYOUT_FQN
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

/**
 * Returns the `@Layout` annotation this string is the path argument for, or null if it isn't one.
 */
fun KtStringTemplateExpression.findOwningLayoutAnnotation(): KtAnnotationEntry? {
    if (hasInterpolation()) return null
    val argument = parent as? KtValueArgument ?: return null
    val annotationEntry = (argument.parent as? KtValueArgumentList)?.parent as? KtAnnotationEntry ?: return null
    return annotationEntry.takeIf { it.isAnnotationOfType(LAYOUT_FQN) }
}

/**
 * Adds a [LayoutPathReference] to the string inside of `@Layout("...")` annotations.
 *
 * This is what powers code completion, navigation, and refactoring support for layout paths.
 */
class LayoutPathReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val stringExpr = element as KtStringTemplateExpression
                    if (stringExpr.findOwningLayoutAnnotation() == null) return PsiReference.EMPTY_ARRAY
                    if (!stringExpr.isInReadableKobwebProject()) return PsiReference.EMPTY_ARRAY

                    return arrayOf(LayoutPathReference(stringExpr))
                }
            }
        )
    }
}
