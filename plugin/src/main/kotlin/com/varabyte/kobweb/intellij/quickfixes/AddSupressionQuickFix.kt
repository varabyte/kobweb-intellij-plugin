package com.varabyte.kobweb.intellij.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPsiFactory

class AddSuppressionQuickFix(private val suppressKey: String) : LocalQuickFix {
    override fun getFamilyName() = "Annotate with @Suppress(\"$suppressKey\")"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val modifierListOwner = PsiTreeUtil.getParentOfType(descriptor.psiElement, KtModifierListOwner::class.java) ?: return
        val modifierList = modifierListOwner.modifierList ?: return

        val annotationText = "@Suppress(\"$suppressKey\")"
        val annotationEntry = KtPsiFactory(project).createAnnotationEntry(annotationText)
        modifierList.addBefore(annotationEntry, modifierList.firstChild)
    }
}