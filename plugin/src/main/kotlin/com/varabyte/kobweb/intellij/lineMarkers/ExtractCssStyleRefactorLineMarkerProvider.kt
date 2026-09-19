package com.varabyte.kobweb.intellij.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.NonEmptyInputValidator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.util.compose.STYLE_PROPERTY_VALUE_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.modifier.MODIFIER_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierCompanion
import com.varabyte.kobweb.intellij.util.kobweb.project.KobwebLineMarkerInfo
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonCallableId
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonClassId
import com.varabyte.kobweb.intellij.util.text.TruncateAt
import com.varabyte.kobweb.intellij.util.text.truncate
import org.bouncycastle.math.raw.Mod
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter

private const val ACTION_TITLE = "Extract Inline Modifier to CssStyle"

class ExtractCssStyleRefactorLineMarkerProvider : LineMarkerProvider {
    private class ModifierChainInfo(val entries: List<Entry>) {
        class Entry(
            val webModifier: KtNamedFunction,
            val webModifierType: WebModifierType,
            val parameters: BoundModifierParameter,
        )
    }
    private class BoundModifierParameter(
        val parameter: KtParameter,
        val type: ClassId,
        val valueExpr: KtExpression?,
    )

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val namedExpression = element as? KtNameReferenceExpression ?: return null
        // Early quick check abort to avoid potentially unnecessary analyze
        if (namedExpression.text != MODIFIER_CLASS_ID.shortClassName.identifier) return null

        // Get the element as the first item of a modifier chain
        val modifierChainStart = PsiTreeUtil.getParentOfType(element, KtDotQualifiedExpression::class.java) ?: return null
        // Should never happen but think of this like an assertion that we ARE the first in the chain
        if (modifierChainStart.receiverExpression != element) return null

        analyze(namedExpression) {
            if (!namedExpression.isModifierCompanion()) return null
        }

        if (isInsideExcludedContext(element)) return null

        return KobwebLineMarkerInfo(
            element,
            ACTION_TITLE,
            navHandler = { _, _ ->
                handleExtractCssStyle(modifierChainStart)
            },
        )
    }

    private fun isInsideExcludedContext(element: PsiElement): Boolean {
        var curr: PsiElement? = element.parent
        while (curr != null && curr !is KtFile) {
            if (curr is KtCallExpression) {
                analyze(curr) {
                    if (curr.styleSingletonCallableId != null) return true
                }
            } else if (curr is KtClass) {
                analyze(curr) {
                    if (curr.expressionType?.styleSingletonClassId != null) return true
                }
            }
            curr = curr.parent
        }
        return false
    }

    private fun handleExtractCssStyle(modifierChainStart: KtDotQualifiedExpression) {
        val project = modifierChainStart.project
        val styleSuffix = "Style"
        val defaultStyleName = "My$styleSuffix"

        val modifierDisplayText = modifierChainStart.text.replace(Regex("\n *"), "")
        val styleName = Messages.showInputDialog(
            project,
            "<html>Target for extraction:<br><br><code>${modifierDisplayText.truncate(32)}</code><br><br>Enter a name for the new CssStyle:</html>",
            ACTION_TITLE,
            Messages.getQuestionIcon(),
            defaultStyleName,
            NonEmptyInputValidator(),
            TextRange(0, defaultStyleName.indexOf(styleSuffix))
        ) ?: return

        // TODO: "This modifier chain includes attribute modifiers. Keep them inline or attach them to the CssStyle?"

        // Use StyleVariables as a way to support modifiers whose values are set to local variables.
        //
        // For example:
        // ```
        // // BEFORE
        // @Composabile
        // fun SomeComposable {
        //   val myColor = Colors.Green
        //   Modifier.backgroundColor(myColor)
        // }
        //
        // // AFTER
        // private val WidgetStyleBackgroundColorVar by StyleVariable<CssColorValue>()
        // val WidgetStyle = CssStyle {
        //    base { Modifier.backgroundColor(WidgetStyleBackgroundColorVar.value()) }
        // }
        // @Composabile
        // fun SomeComposable {
        //   val myColor = Colors.Green
        //   WidgetStyle.toModifier()
        //     .setVariable(WidgetStyleBackgroundColorVar, myColor)
        // }
        // ```

        val modifierChain = analyze(modifierChainStart) {
            modifierChainStart.toModifierChainInfo()
        }

//        // Perform PSI mutation
//        performRefactoring(modifierChain, styleName, extractedVars)
    }

    context(kaSession: KaSession)
    private fun KtDotQualifiedExpression.toModifierChainInfo(): ModifierChainInfo = with(kaSession) {
        val entries = mutableListOf<ModifierChainInfo.Entry>()

        val chainedCalls = mutableListOf<KtCallExpression>()
        var current: KtExpression? = this@toModifierChainInfo
        while (current is KtDotQualifiedExpression) {
            var selector = current.selectorExpression
            current = current.receiverExpression
        }

        return ModifierChainInfo(entries)
    }


//    context(kaSession: KaSession)
//    private fun collectExtractedVariables(
//        modifierChainStart: KtDotQualifiedExpression,
//    ): List<StyleModifierParameter> = with(kaSession) {
//        val extracted = mutableListOf<StyleModifierParameter>()
//
//        val chainedCalls = mutableListOf<KtCallExpression>()
//        var current: KtExpression? = modifierChainStart
//
//        while (current is KtDotQualifiedExpression) {
//            val selector = current.selectorExpression
//            if (selector is KtCallExpression) {
//                chainedCalls.add(0, selector) // Keep left-to-right order
//            }
//            current = current.receiverExpression
//        }
//
//        for (call in chainedCalls) {
//            call.
//            val propertyName = call.calleeExpression?.text ?: continue
//            val valueArguments = call.valueArguments
//
//            for (arg in valueArguments) {
//                val expr = arg.getArgumentExpression() ?: continue
//
//                if (expr.referencesLocalScope()) {
//                    val resolvedType = expr.expressionType?.expandedSymbol?.classId ?: STYLE_PROPERTY_VALUE_CLASS_ID
//                    val varName = deriveVariableName(styleName, propertyName)
//
//                    extracted.add(
//                        StyleModifierParameter(
//                            varName = varName,
//                            propertyName = propertyName,
//                            typeClassId = resolvedType,
//                            originalExprText = expr.text
//                        )
//                    )
//                }
//            }
//        }
//
//        return extracted
//    }

    /**
     * Check if the current expression references a variable in local scope.
     *
     * If so, it means we'll need to be careful about how we extract this modifier to a CssStyle.
     */
    context(kaSession: KaSession)
    private fun KtExpression.referencesLocalScope(): Boolean = with(kaSession) {
        return false
    }
}