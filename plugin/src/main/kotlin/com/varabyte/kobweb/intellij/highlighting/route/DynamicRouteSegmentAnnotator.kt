package com.varabyte.kobweb.intellij.highlighting.route

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.highlighting.KobwebHighlightingColors
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.DynamicRouteSegment
import com.varabyte.kobweb.intellij.util.kobweb.route.findLedToBy
import com.varabyte.kobweb.intellij.util.kobweb.route.findPages
import com.varabyte.kobweb.intellij.util.kobweb.route.isRoutePathArgument
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPath
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Highlights the values that fill the dynamic segments of the page a route leads to, e.g. "me" and "123" in
 * `Link("/author/me/post/123")` for a page served at "/author/{author}/post/{post}".
 *
 * Hovering over a value shows which segment it fills.
 */
class DynamicRouteSegmentAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val stringExpr = element as? KtStringTemplateExpression ?: return
        if (stringExpr.parent !is KtValueArgument) return
        val path = stringExpr.linkPath ?: return
        if (!stringExpr.isInReadableKobwebProject()) return

        val page = stringExpr.findPages().findLedToBy(path) ?: return
        val dynamicSegments = page.route.segments.map(DynamicRouteSegment::tryCreate)
        if (dynamicSegments.all { it == null }) return
        if (!analyze(stringExpr) { stringExpr.isRoutePathArgument() }) return

        for ((i, dynamicSegment) in dynamicSegments.withIndex()) {
            if (dynamicSegment == null) continue
            // A catch-all segment is filled by the rest of the path
            val indices = (if (dynamicSegment.isCatchAll) i..path.segments.lastIndex else listOf(i))
                .filter { index -> path.segments.getOrNull(index)?.sourceRange?.isEmpty() == false }
            if (indices.isEmpty()) continue
            // e.g. "a/b/c" for a catch-all segment filled by "/a/b/c", even though each part is highlighted separately
            val value = stringExpr.text.substring(
                path.segments[indices.first()].sourceRange.first,
                path.segments[indices.last()].sourceRange.last + 1,
            )

            // Template expressions (i.e. segments whose values are unknown) are already highlighted as code
            for (index in indices.filter { index -> path.segments[index].text != null }) {
                val range = path.segments[index].sourceRange.let { range -> TextRange(range.first, range.last + 1) }
                holder.newAnnotation(
                    HighlightSeverity.INFORMATION,
                    UxGlobals.textBundle.message(
                        "highlighting.dynamic.route.segment.tooltip",
                        value,
                        dynamicSegment.rawValue,
                        page.route.value,
                    ),
                )
                    .range(range.shiftRight(stringExpr.textRange.startOffset))
                    .textAttributes(KobwebHighlightingColors.DYNAMIC_ROUTE_SEGMENT_VALUE)
                    .create()
            }
        }
    }
}
