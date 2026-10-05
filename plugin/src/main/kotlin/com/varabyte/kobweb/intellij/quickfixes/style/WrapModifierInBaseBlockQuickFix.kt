package com.varabyte.kobweb.intellij.quickfixes.style

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleSheetBlock
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * A quick fix to wrap a modifier chain inside a `base { ... }` block, if we're inside a CSS style block.
 */
class WrapModifierInBaseBlockQuickFix : KotlinModCommandQuickFix<KtExpression>(), HighPriorityAction {
    override fun getFamilyName() = getName()
    override fun getName() = "Wrap modifier chain in `base { ... }` block"

    override fun applyFix(
        project: Project,
        element: KtExpression,
        updater: ModPsiUpdater
    ) {
        val styleBlock = analyze(element) {
            StyleSheetBlock.Style.containing(element)?.takeIf { it.baseCall == null } ?: return
        }

        val styleScope = styleBlock.styleScope
        val factory = KtPsiFactory(project)
        val addedElement = styleScope.addBefore(factory.createExpression("base { ${element.text} }"), styleScope.firstChild)
        styleScope.addAfter(factory.createNewLine(), addedElement)
        element.delete()
    }
}