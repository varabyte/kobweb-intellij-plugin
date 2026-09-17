package com.varabyte.kobweb.intellij.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner

class MakePublicQuickFix(private val targetName: String) : LocalQuickFix {
    override fun getName(): @IntentionName String = "Make `$targetName` public"
    override fun getFamilyName() = "Make declarations public"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val modifierListOwner = PsiTreeUtil.getParentOfType(descriptor.psiElement, KtModifierListOwner::class.java) ?: return
        if ((modifierListOwner as? KtDeclaration)?.name != targetName) return
        modifierListOwner.removeModifier(KtTokens.PRIVATE_KEYWORD)
    }
}