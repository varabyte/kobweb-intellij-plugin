package com.varabyte.kobweb.intellij.inspections.api

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.util.kobweb.api.ApiDefinition
import com.varabyte.kobweb.intellij.util.kobweb.api.ApiPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.api.ApiRoute
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiDefinitions
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.api.mayHaveApiPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.LinkPath.UnknownSegmentsStrategy
import com.varabyte.kobweb.intellij.util.kobweb.route.isFullUrl
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import com.varabyte.kobweb.intellij.util.kobweb.route.parseLinkPath
import com.varabyte.kobweb.intellij.util.psi.unescapedText
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports API paths that don't lead to any API endpoint, e.g. `window.api.get("helo")`, which is usually a typo or a
 * call to an endpoint that was moved or deleted.
 *
 * Only paths used with something that holds one are checked (e.g. `window.api.get`'s `apiPath`), unless it's marked
 * `mustExist = false`. API paths with values that aren't known until runtime (e.g. `"users/$id"`) are only reported if
 * none of their possible values could lead to an endpoint, while stream paths with them aren't checked at all.
 */
class UnknownApiPathInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)
                if (!expression.mayHaveApiPathTarget()) return
                // Reported by `InvalidApiPathFormatInspection` instead
                if (expression.isFullUrl) return

                val target = analyze(expression) { expression.findApiPathTarget() } ?: return
                if (!target.mustExist) return

                val definitions = expression.findApiDefinitions()
                when (target.kind) {
                    ApiPathTarget.Kind.API -> checkApiPath(expression, definitions.apis)
                    ApiPathTarget.Kind.STREAM -> checkStreamPath(expression, definitions.streams)
                }
            }

            private fun checkApiPath(expression: KtStringTemplateExpression, apis: List<ApiDefinition>) {
                val path = expression.parseLinkPath(allowRelativePath = true) ?: return
                // Unknown segments could hold anything at runtime, so only report paths that can't lead to any endpoint
                if (apis.any { api -> path.matches(api.route, UnknownSegmentsStrategy.MATCH_ANYTHING) }) return

                // A common mistake is to include the "/api" prefix, e.g. "/api/hello" (for `/api/api/hello`)
                val includesApiPrefix = apis.any { api ->
                    path.matches(ApiRoute("/api${api.route.value}"), UnknownSegmentsStrategy.MATCH_ANYTHING)
                }
                holder.registerProblem(
                    expression,
                    expression.linkPathRange(),
                    UxGlobals.textBundle.message(
                        if (includesApiPrefix) {
                            "inspection.unknown.api.path.api.prefix.display.text"
                        } else {
                            "inspection.unknown.api.path.display.text"
                        }
                    ),
                )
            }

            private fun checkStreamPath(expression: KtStringTemplateExpression, streams: List<ApiDefinition>) {
                if (expression.hasInterpolation()) return
                val path = expression.unescapedText.removePrefix("/")
                // Reported by `InvalidApiPathFormatInspection` instead
                if ('?' in path || '#' in path) return
                if (streams.any { stream -> stream.route.value.removePrefix("/") == path }) return

                holder.registerProblem(
                    expression,
                    ElementManipulators.getValueTextRange(expression),
                    UxGlobals.textBundle.message("inspection.unknown.api.path.stream.display.text"),
                )
            }
        }
    }
}
