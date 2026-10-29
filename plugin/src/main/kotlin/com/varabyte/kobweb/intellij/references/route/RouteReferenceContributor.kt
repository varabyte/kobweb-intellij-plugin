package com.varabyte.kobweb.intellij.references.route

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.mayHavePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.parseLinkPath
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Adds references to strings that look like routes and are used in a way that could hold one (see
 * [mayHavePathTarget]), e.g. `"/blog/post"` or `"/images/cat.png"` passed to `Link`, whose parameter is annotated
 * as a route path.
 *
 * We won't know whether a route leads to a page or a public file until its references resolve, so this contributor
 * attaches both: a [RouteReference] for the page, and a [PublicFileReference] for each segment of the file's path. Only
 * one kind of them (or none!) will actually resolve, once analysis reveals more.
 *
 * This is what powers navigation from a route to its page or public file (and finding their usages), and what updates
 * routes when the public files they lead to are renamed or moved.
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
                    if (!stringExpr.mayHavePathTarget()) return PsiReference.EMPTY_ARRAY
                    val path = stringExpr.parseLinkPath() ?: return PsiReference.EMPTY_ARRAY
                    if (!stringExpr.isInReadableKobwebProject()) return PsiReference.EMPTY_ARRAY

                    // Paths with segments that aren't known until runtime can't lead to public files
                    val publicFileReferences = if (path.segments.all { segment -> segment.text != null }) {
                        path.segments.withIndex()
                            .filter { (_, segment) -> segment.text != "" }
                            .map { (i, segment) ->
                                val range = segment.sourceRange
                                PublicFileReference(stringExpr, i, TextRange(range.first, range.last + 1))
                            }
                    } else emptyList()

                    return arrayOf(RouteReference(stringExpr), *publicFileReferences.toTypedArray())
                }
            }
        )
    }
}
