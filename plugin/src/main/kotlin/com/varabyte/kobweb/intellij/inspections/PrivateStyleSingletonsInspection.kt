package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.AddSuppressionQuickFix
import com.varabyte.kobweb.intellij.quickfixes.AddUnderscorePrefixQuickFix
import com.varabyte.kobweb.intellij.quickfixes.MakePublicQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isUsedInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonClassId
import com.varabyte.kobweb.intellij.util.psi.isSuppressedWith
import com.varabyte.kobweb.intellij.util.text.camelCaseToScreamingSnakeCase
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

class PrivateStyleSingletonsInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)

                if (!property.isUsedInWritableKobwebProject()) return

                val nameIdentifier = property.nameIdentifier ?: return

                val matchingClassId: ClassId = analyze(property) {
                    property.initializer?.expressionType?.styleSingletonClassId ?: return
                }

                val suppressKey = "PRIVATE_${matchingClassId.shortClassName.identifier.camelCaseToScreamingSnakeCase()}"
                if (property.isPrivate() && !(
                            nameIdentifier.text.startsWith("_") || property.isSuppressedWith(suppressKey))
                ) {

                    // Ideally target the "private" keyword, but with a fallback in case we can't find it unexpectedly
                    val problemElement = property.modifierList
                        ?.node
                        ?.findChildByType(KtTokens.PRIVATE_KEYWORD)
                        ?.psi
                        ?: nameIdentifier

                    holder.registerProblem(
                        problemElement,
                        "Properties of type `${matchingClassId.shortClassName}` should be public, so that Kobweb can discover and register them.",
                        MakePublicQuickFix(nameIdentifier.text),
                        AddSuppressionQuickFix(suppressKey),
                        AddUnderscorePrefixQuickFix(nameIdentifier.text),
                    )
                }
            }
        }
    }
}