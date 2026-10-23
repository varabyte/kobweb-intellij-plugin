package com.varabyte.kobweb.intellij.references.route

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPath
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Adds a [RouteReference] to strings passed into functions that look like routes, e.g. `Link("/blog/post")`.
 *
 * This is what powers navigation from a route to its page (and finding a page's usages).
 */
class RouteReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val stringExpr = element as KtStringTemplateExpression
                    // Checking whether the argument actually takes a route requires Kotlin analysis, which is too
                    // expensive to do here, so the reference checks that when it resolves
                    if (stringExpr.parent !is KtValueArgument) return PsiReference.EMPTY_ARRAY
                    if (stringExpr.linkPath == null) return PsiReference.EMPTY_ARRAY
                    if (!stringExpr.isInReadableKobwebProject()) return PsiReference.EMPTY_ARRAY

                    return arrayOf(RouteReference(stringExpr))
                }
            }
        )
    }
}
