package com.varabyte.kobweb.intellij.quickfixes

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class AddUnderscorePrefixQuickFix(private val targetName: String) : LocalQuickFix, LowPriorityAction {
    init {
        require(!targetName.startsWith("_")) { "Underscore quick fix target name should not itself start with an underscore: `$targetName`"}
    }

    override fun getName(): @IntentionName String =
        UxGlobals.textBundle.message("quickfixes.add_underscore_prefix.text", targetName)
    override fun getFamilyName() = UxGlobals.textBundle.message("quickfixes.add_underscore_prefix.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val declaration = PsiTreeUtil.getParentOfType(descriptor.psiElement, KtNamedDeclaration::class.java) ?: return
        val currentName = declaration.name?.takeIf { it == targetName } ?: return

        val newName = "_$currentName"

        val references = ReferencesSearch.search(declaration).findAll()
        for (ref in references) ref.handleElementRename(newName)
        @Suppress("UsePropertyAccessSyntax") // Property syntax results in a compile error
        declaration.setName(newName)
    }
}