package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.MovePropertyToTopLevelQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isUsedInWritableKobwebProject
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.isPublic

private val SILK_STYLE_PACKAGE = FqName("com.varabyte.kobweb.silk.style")
private val SILK_STYLE_ANIMATION_PACKAGE = SILK_STYLE_PACKAGE.child(Name.identifier("animation"))
private val CSS_STYLE_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("CssStyle"))
private val CSS_STYLE_VARIANT_ID = ClassId(SILK_STYLE_PACKAGE, Name.identifier("CssStyleVariant"))
private val KEYFRAMES_ID = ClassId(SILK_STYLE_ANIMATION_PACKAGE, Name.identifier("Keyframes"))

class TopLevelPropertiesInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)

                if (!property.isUsedInWritableKobwebProject()) return

                val nameIdentifier = property.nameIdentifier ?: return

                val matchingClassId: ClassId? = analyze(property) {
                    val type = property.initializer?.expressionType ?: return@analyze null
                    type.expandedSymbol?.classId?.takeIf { it in setOf(CSS_STYLE_ID, CSS_STYLE_VARIANT_ID, KEYFRAMES_ID) }
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