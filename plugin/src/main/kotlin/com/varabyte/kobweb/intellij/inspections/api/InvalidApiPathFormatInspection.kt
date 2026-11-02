package com.varabyte.kobweb.intellij.inspections.api

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.util.kobweb.api.ApiPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.api.mayHaveApiPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.isFullUrl
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathEscapesRoot
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports API paths that aren't in the format expected by what they're used with, e.g. a full URL passed to
 * `window.api.get` (which only takes a path relative to "/api/"), a path that uses ".." to leave "/api/", or query
 * parameters in a stream path (which must match a stream's route exactly).
 */
class InvalidApiPathFormatInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)
                if (!expression.mayHaveApiPathTarget()) return

                val isFullUrl = expression.isFullUrl
                val valueRange = ElementManipulators.getValueTextRange(expression)
                // Everything after the path, if anything, is the query parameters and fragment
                val queryAndFragmentRange = TextRange(expression.linkPathRange().endOffset, valueRange.endOffset)
                    .takeIf { range -> !range.isEmpty }
                val escapesRoot = !isFullUrl && expression.linkPathEscapesRoot()
                // Checked before analysis, as most strings are fine whatever the target holds
                if (!isFullUrl && !escapesRoot && queryAndFragmentRange == null) return

                val target = analyze(expression) { expression.findApiPathTarget() } ?: return
                when (target.kind) {
                    ApiPathTarget.Kind.API -> when {
                        isFullUrl -> holder.registerProblem(
                            expression,
                            valueRange,
                            UxGlobals.textBundle.message("inspection.invalid.api.path.format.full.url.display.text"),
                        )

                        escapesRoot -> holder.registerProblem(
                            expression,
                            expression.linkPathRange(),
                            UxGlobals.textBundle.message(
                                "inspection.invalid.api.path.format.escapes.root.display.text"
                            ),
                        )
                    }

                    ApiPathTarget.Kind.STREAM -> when {
                        isFullUrl -> holder.registerProblem(
                            expression,
                            valueRange,
                            UxGlobals.textBundle.message(
                                "inspection.invalid.api.path.format.stream.full.url.display.text"
                            ),
                        )

                        queryAndFragmentRange != null -> holder.registerProblem(
                            expression,
                            queryAndFragmentRange,
                            UxGlobals.textBundle.message("inspection.invalid.api.path.format.stream.query.display.text"),
                        )
                    }
                }
            }
        }
    }
}
