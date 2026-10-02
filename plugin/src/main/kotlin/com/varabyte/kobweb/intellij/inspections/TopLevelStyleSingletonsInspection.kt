package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.MovePropertyToTopLevelQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isDeclaredInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSheetSingletonClassId
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*

class TopLevelStyleSingletonsInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        if (!holder.file.isDeclaredInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)

                val nameIdentifier = property.nameIdentifier ?: return

                val matchingClassId: ClassId? = analyze(property) {
                    property.initializer?.expressionType?.styleSheetSingletonClassId ?: return@analyze null
                }

                if (matchingClassId == null) return

                var isTopLevel = true
                var curr: PsiElement? = property.parent
                while (curr != null && curr !is KtFile) {
                    // object { ... } is an object declaration parenting a class body
                    if (!(curr is KtObjectDeclaration || curr is KtClassBody)) {
                        isTopLevel = false
                        break
                    }
                    curr = curr.parent
                }
                if (!isTopLevel) {
                    holder.registerProblem(
                        nameIdentifier,
                        "Properties of type `${matchingClassId.shortClassName}` must be declared at the top level of your file (or inside top-level objects).",
                        MovePropertyToTopLevelQuickFix(nameIdentifier.text)
                    )
                }
            }
        }
    }
}