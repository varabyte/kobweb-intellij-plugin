package com.varabyte.kobweb.intellij.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.inspections.TopLevelPropertiesInspection
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * A quick fix to help move various Kobweb properties to the top level.
 *
 * Some property types (like `CssStyle` and `Keyframes`) have to live at a top-level as singletons so that Kobweb can
 * find and register them.
 *
 * See also: [TopLevelPropertiesInspection].
 */
class MovePropertyToTopLevelQuickFix(private val propertyName: String) : LocalQuickFix {
    override fun getName(): @IntentionName String {
        return "Move '$propertyName' to the top level"
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return "Move properties to the top level"
    }

    override fun startInWriteAction() = true

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val property = descriptor.psiElement as? KtProperty
            ?: descriptor.psiElement.parent as? KtProperty
            ?: return

        val file = property.containingFile as? KtFile ?: return
        val psiFactory = KtPsiFactory(project)

        // 1. Find the top-level anchor: walk up until our parent is the KtFile itself
        var anchor: PsiElement = property
        var parent = property.parent
        while (parent != null && parent !is KtFile) {
            anchor = parent
            parent = parent.parent
        }

        // If we found a valid top-level anchor (e.g., the class/function containing it),
        // insert the new property right before it.
        val newPropertyText = property.text
        val addedProperty = file.addBefore(psiFactory.createProperty(newPropertyText), anchor)

        // Add a newline after the inserted property for clean spacing
        file.addAfter(psiFactory.createNewLine(), addedProperty)

        // Delete the original misplaced property
        property.delete()
    }
}