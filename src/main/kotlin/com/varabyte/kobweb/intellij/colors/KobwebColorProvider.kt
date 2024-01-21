@file:Suppress("UseJBColor")

package com.varabyte.kobweb.intellij.colors

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.js.translate.declaration.hasCustomGetter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import java.awt.Color

// This prevents the color tracer from checking the codebase ridiculously deep,
// which might cause lag or even worse a stackoverflow
private const val MAX_SEARCH_DEPTH = 15

private const val KOBWEB_COLOR_COMPANION_FQ_NAME = "com.varabyte.kobweb.compose.ui.graphics.Color.Companion"

/**
 * Enables showing those small rectangular color previews next to the line number
 */
class KobwebColorProvider : ElementColorProvider {

    override fun getColorFrom(element: PsiElement): Color? = when {
        element !is LeafPsiElement -> null
        element.elementType != KtTokens.IDENTIFIER -> null
        element.parent is KtProperty -> null // Avoid showing multiple previews
        else -> traceColor(element.parent) // Leaf is just text. The parent is the actual object
    }

    /**
     * Setting colors is not (yet) supported
     */
    override fun setColorTo(element: PsiElement, color: Color) = Unit
}

/**
 * Tries resolving references as deep as possible and checks if a Kobweb color is being referred to
 */
private fun traceColor(element: PsiElement, currentDepth: Int = 0): Color? {
    val nextElement = when (element) {
        is KtDotQualifiedExpression -> element.selectorExpression

        is KtNameReferenceExpression -> when {
            element.parent is KtCallExpression -> element.parent // Element is name of a function
            else -> element.locateSource()
        }

        is KtProperty -> when {
            element.hasInitializer() -> element.initializer
            element.hasCustomGetter() -> element.getter
            else -> null
        }

        is KtPropertyAccessor -> element.bodyExpression

        is KtCallExpression -> (null).apply {
            val callee = ((element.calleeExpression as? KtNameReferenceExpression)?.locateSource() as? KtNamedFunction)
                ?: return@apply

            when {
                callee.isKobwebColorFunction("rgb(r: Int, g: Int, b: Int)") -> run {
                    val args = element.valueArguments.evaluateArguments<Int>(3) ?: return@run
                    return safeRgbColor(args[0], args[1], args[2])
                }

                callee.isKobwebColorFunction("rgb(value: Int)") -> run {
                    val args = element.valueArguments.evaluateArguments<Int>(1) ?: return@run
                    return safeRgbColor(args[0])
                }

                callee.isKobwebColorFunction("rgba(value: Int)", "rgba(value: Long)") -> run {
                    val args = element.valueArguments.run {
                        evaluateArguments<Int>(1) ?: evaluateArguments<Long, Int>(1) { it.toInt() }
                    } ?: return@run
                    return safeRgbColor(args[0] shr Byte.SIZE_BITS)
                }

                callee.isKobwebColorFunction("argb(value: Int)", "argb(value: Long)") -> run {
                    val args = element.valueArguments.run {
                        evaluateArguments<Int>(1) ?: evaluateArguments<Long, Int>(1) { it.toInt() }
                    } ?: return@run
                    return safeRgbColor(args[0] and 0x00_FF_FF_FF)
                }
            }
        }

        else -> null
    }

    return if (currentDepth <= MAX_SEARCH_DEPTH) {
        nextElement?.let { traceColor(it, currentDepth + 1) }
    } else null
}

/**
 * Pretty much emulates what CTRL-clicking on a reference would do
 */
private fun KtSimpleNameExpression.locateSource(): PsiElement? {
    val resolvedMainReference = this.mainReference.resolve() ?: return null
    val sourceDescriptor =
        PsiNavigationSupport.getInstance().getDescriptor(resolvedMainReference) as? OpenFileDescriptor ?: return null
    val sourcePsiFile = sourceDescriptor.file.toPsiFile(this.project) ?: return null
    val sourceElementIdentifier = PsiUtilBase.getElementAtOffset(sourcePsiFile, sourceDescriptor.offset)
    val sourceElement = sourceElementIdentifier.parent

    return sourceElement
}

/**
 * Uses the kotlin analysis api, as it can parse the constants much smarter.
 * It can parse decimal, hex and binary automatically for example.
 */
private inline fun <reified Evaluated, reified Mapped> Collection<KtValueArgument>.evaluateArguments(
    argCount: Int,
    evaluatedValueMapper: (Evaluated) -> Mapped
): Array<Mapped>? {
    val constantExpressions = this.mapNotNull { it.getArgumentExpression() as? KtConstantExpression }

    val evaluatedArguments = constantExpressions.mapNotNull {
        analyze(it.containingKtFile) {
            it.evaluate(KtConstantEvaluationMode.CONSTANT_LIKE_EXPRESSION_EVALUATION)?.value as? Evaluated
        }
    }

    return if (evaluatedArguments.size != this.size || evaluatedArguments.size != argCount) null
    else evaluatedArguments.map(evaluatedValueMapper).toTypedArray()
}

private inline fun <reified Evaluated> Collection<KtValueArgument>.evaluateArguments(argCount: Int) =
    evaluateArguments<Evaluated, Evaluated>(argCount) { it }

private fun KtNamedFunction.isKobwebColorFunction(vararg functionSignatures: String): Boolean {
    val actualFqName = this.kotlinFqName?.asString() ?: return false
    val actualParameters = this.valueParameterList?.text ?: return false
    val actual = actualFqName + actualParameters

    return functionSignatures.any { functionSignature ->
        val expected = "$KOBWEB_COLOR_COMPANION_FQ_NAME.$functionSignature"
        expected == actual
    }
}

private fun safeRgbColor(r: Int, g: Int, b: Int) =
    runCatching { Color(r, g, b) }.getOrNull()

private fun safeRgbColor(rgb: Int) =
    runCatching { Color(rgb) }.getOrNull()