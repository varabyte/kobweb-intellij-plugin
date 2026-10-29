package com.varabyte.kobweb.intellij.references.api

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import com.varabyte.kobweb.intellij.util.kobweb.api.mayHaveApiPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Adds an [ApiPathReference] to strings whose use suggests they might hold an API path (see [mayHaveApiPathTarget]),
 * e.g. `"hello"` passed to `window.api.get`, or `"chat"` passed to `ApiStream`.
 *
 * This is what powers navigation from an API path to its `@Api` function or `ApiStream` property.
 */
class ApiPathReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val stringExpr = element as KtStringTemplateExpression
                    // Checking whether the string actually holds an API path requires Kotlin analysis, which is too
                    // expensive to do here, so the reference checks that when it resolves
                    if (!stringExpr.mayHaveApiPathTarget()) return PsiReference.EMPTY_ARRAY
                    if (!stringExpr.isInReadableKobwebProject()) return PsiReference.EMPTY_ARRAY
                    return arrayOf(ApiPathReference(stringExpr))
                }
            }
        )
    }
}
