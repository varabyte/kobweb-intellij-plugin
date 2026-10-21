package com.varabyte.kobweb.intellij.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierListOwner

class MakePublicQuickFix(private val targetName: String) : LocalQuickFix {
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.make_public.name")
    override fun getName(): @IntentionName String =
        UxGlobals.textBundle.message("quickfixes.make_public.text", targetName)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val modifierListOwner = PsiTreeUtil.getParentOfType(descriptor.psiElement, KtModifierListOwner::class.java) ?: return
        if ((modifierListOwner as? KtDeclaration)?.name != targetName) return
        modifierListOwner.modifierList?.getModifier(KtTokens.PRIVATE_KEYWORD)?.delete()
    }
}