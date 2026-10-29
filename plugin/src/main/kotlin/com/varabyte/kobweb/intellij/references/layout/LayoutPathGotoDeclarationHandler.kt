package com.varabyte.kobweb.intellij.references.layout

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Lets the whole path inside a `@Layout("...")` annotation navigate to its layout, e.g. Ctrl/Cmd+clicking anywhere in
 * `".components.layouts.PageLayout"`, not just on `PageLayout`.
 *
 * The [LayoutPathReference] only covers the layout's name, as that's the only part a rename changes, so on its own,
 * only the name would be clickable. The IDE asks handlers like this one before references, and underlines the text
 * under the pointer, so the whole path is underlined and leads to the layout.
 */
class LayoutPathGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val stringExpr = sourceElement?.parentOfType<KtStringTemplateExpression>() ?: return null
        val reference = stringExpr.references.filterIsInstance<LayoutPathReference>().singleOrNull() ?: return null
        return reference.resolve()?.let { layout -> arrayOf(layout) }
    }
}
