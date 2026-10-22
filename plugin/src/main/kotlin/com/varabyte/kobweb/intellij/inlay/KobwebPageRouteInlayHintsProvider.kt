package com.varabyte.kobweb.intellij.inlay

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.PAGE_FQN
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoute
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Shows the route that Kobweb generates for each page, right after its `@Page` annotation.
 *
 * Routes come from a page's package and file name (plus any `@file:PackageMapping` annotations and the `@Page`
 * annotation's route override), so it's easy to lose track of what they end up being.
 */
class KobwebPageRouteInlayHintsProvider : InlayHintsProvider {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        if (!file.isInReadableKobwebProject()) return null

        return object : SharedBypassCollector {
            override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
                val page = element as? KtNamedFunction ?: return
                val route = page.findRoute() ?: return
                val pageAnnotation = page.annotationEntries.first { it.isAnnotationOfType(PAGE_FQN) }

                // If the page spells out its full route already, there's nothing to add
                val routeOverride = pageAnnotation.valueArguments.firstOrNull()?.getArgumentExpression()
                if (routeOverride is KtStringTemplateExpression && ElementManipulators.getValueText(routeOverride) == route) {
                    return
                }

                sink.addPresentation(
                    // Note: We use an end-of-line hint (instead of an inline one right after the annotation) because
                    // IntelliJ truncates long inline hints, and we want to show routes in full. Usually, the `@Page`
                    // annotation is on its own line so this is almost always fine.
                    EndOfLinePosition(editor.document.getLineNumber(pageAnnotation.textRange.endOffset)),
                    tooltip = UxGlobals.textBundle.message("kobweb.inlay.hints.page.route.tooltip", route),
                    hintFormat = HintFormat.default,
                ) {
                    text(route)
                }
            }
        }
    }

    companion object {
        const val PROVIDER_ID = "kobweb.page.route.hints"
    }
}
