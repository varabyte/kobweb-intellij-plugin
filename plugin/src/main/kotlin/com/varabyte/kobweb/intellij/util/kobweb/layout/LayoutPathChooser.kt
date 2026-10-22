@file:Suppress("UnstableApiUsage")

package com.varabyte.kobweb.intellij.util.kobweb.layout

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.util.text.EditDistance
import com.varabyte.kobweb.intellij.util.kobweb.PackageShortcuts
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Lets the user choose a layout for a `@Layout` annotation from a popup, updating (or adding) the annotation with their
 * choice.
 */
object LayoutPathChooser {
    /**
     * Returns a command that shows a popup of all layouts a `@Layout` annotation could point at.
     *
     * Layouts that best match the current path are listed first, so a typo'd path puts the intended layout at the top
     * (and a valid path puts the current layout at the top).
     *
     * @param element Either the annotation's path, which will be replaced, the annotation itself, whose path will be
     *   replaced (or added, if it doesn't have one, e.g. `@Layout`), or a page without a `@Layout` annotation, which
     *   will have one added (in which case the default layout it already uses, if any, isn't offered).
     */
    fun choose(element: PsiElement): ModCommand {
        val currentName = element.currentPath.substringAfterLast('.')

        fun LayoutDefinition.distanceFromCurrentName(): Int {
            // Without a name to compare against, edit distance would just favor shorter names
            return if (currentName.isEmpty()) 0 else EditDistance.levenshtein(currentName, shortName, false)
        }

        val group = element.kobwebGroup
        val paths = findChoices(element)
            .sortedWith(compareBy({ it.distanceFromCurrentName() }, { it.fqn }))
            .map { layout -> PackageShortcuts.relativize(group, FqName(layout.fqn)) }

        if (paths.isEmpty()) return ModCommand.nop()
        return ModCommand.chooseAction(
            UxGlobals.textBundle.message("popups.layout.chooser.title"),
            paths.map { path -> SetLayoutPathAction(element, path) },
        )
    }

    /**
     * Returns true if [choose] would offer at least one layout other than the one [element] already points at, i.e. if
     * choosing could actually change anything.
     */
    fun hasChoices(element: PsiElement): Boolean {
        val currentFqn = PackageShortcuts.resolve(element.kobwebGroup, element.currentPath)?.asString()
        return findChoices(element).any { layout -> layout.fqn != currentFqn }
    }

    private val PsiElement.currentPath: String
        get() {
            val pathExpr = when (this) {
                is KtStringTemplateExpression -> this
                is KtAnnotationEntry -> layoutPathExpression
                else -> null
            }
            return pathExpr?.let(ElementManipulators::getValueText).orEmpty()
        }

    private fun findChoices(element: PsiElement): List<LayoutDefinition> {
        // A layout can't be its own parent
        val annotatedFunction = element.getStrictParentOfType<KtNamedFunction>()
        // Choosing the default layout a page already uses wouldn't change anything
        val defaultLayoutFqn = (element as? KtNamedFunction)?.findDefaultLayout()?.layout?.fqn

        return element.findLayouts()
            .filter { layout -> layout.function == null || layout.function != annotatedFunction }
            .filter { layout -> layout.fqn != defaultLayoutFqn }
    }

    private class SetLayoutPathAction(element: PsiElement, private val path: String) :
        PsiUpdateModCommandAction<PsiElement>(element) {
        override fun getFamilyName() = UxGlobals.textBundle.message("popups.layout.chooser.set.path.command")

        override fun getPresentation(context: ActionContext, element: PsiElement) = Presentation.of(path)

        override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
            when (element) {
                is KtStringTemplateExpression -> ElementManipulators.handleContentChange(element, path)
                // A page without a `@Layout` annotation
                is KtNamedFunction -> element.addLayoutAnnotation(path)
                // The annotation itself, which may or may not have a path already (e.g. `@Layout(".a.BLayout")`,
                // `@Layout`, `@Layout()`, or `@Layout("")`). We update it in place, rather than recreating the
                // annotation, so we keep any use-site target (e.g. `@file:Layout`).
                is KtAnnotationEntry -> {
                    val existingPath = element.layoutPathExpression
                    if (existingPath != null) {
                        ElementManipulators.handleContentChange(existingPath, path)
                        return
                    }

                    val psiFactory = KtPsiFactory(context.project())
                    val argument = psiFactory.createArgument(psiFactory.createStringTemplate(path))
                    element.valueArgumentList?.addArgument(argument)
                        ?: element.add(psiFactory.createCallArguments("(${argument.text})"))
                }
            }
        }
    }
}
