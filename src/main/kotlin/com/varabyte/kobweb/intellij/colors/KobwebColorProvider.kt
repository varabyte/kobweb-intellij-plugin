// ElementColorProvider interface uses standard AWT Color, as no darkened version is needed
@file:Suppress("UseJBColor")

package com.varabyte.kobweb.intellij.colors

import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.js.translate.declaration.hasCustomGetter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import java.awt.Color
import kotlin.math.abs

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
            else -> element.findDeclaration()
        }

        is KtProperty -> when {
            element.hasInitializer() -> element.initializer
            element.hasCustomGetter() -> element.getter
            else -> null
        }

        is KtPropertyAccessor -> element.bodyExpression

        is KtCallExpression -> (null).apply {
            val calleeExpression = element.calleeExpression as? KtNameReferenceExpression ?: return@apply
            val callee = calleeExpression.findDeclaration() as? KtNamedFunction ?: return@apply

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

                callee.isKobwebColorFunction("hsl(h: Float, s: Float, l: Float)") -> run {
                    val args = element.valueArguments.evaluateArguments<Float>(3) ?: return@run
                    return safeHslColor(args[0], args[1], args[2])
                }

                callee.isKobwebColorFunction("hsla(h: Float, s: Float, l: Float, a: Float)") -> run {
                    val args = element.valueArguments.evaluateArguments<Float>(4) ?: return@run
                    return safeHslColor(args[0], args[1], args[2])
                }
            }
        }

        else -> null
    }

    return if (currentDepth <= MAX_SEARCH_DEPTH) {
        nextElement?.let { traceColor(it, currentDepth + 1) }
    } else null
}

// navigationElement returns the element where a feature like "Go to declaration" would point:
// The source declaration, if found, and not a compiled one, which would make further analyzing impossible.
private fun KtSimpleNameExpression.findDeclaration(): PsiElement? = this.mainReference.resolve()?.navigationElement

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

private fun safeHslColor(hue: Float, saturation: Float, lightness: Float): Color? {
    // https://en.wikipedia.org/wiki/HSL_and_HSV#Color_conversion_formulae
    val chroma = (1 - abs(2 * lightness - 1)) * saturation
    val intermediateValue = chroma * (1 - abs(((hue / 60) % 2) - 1))
    val hueSection = (hue.toInt() % 360) / 60
    val r: Float
    val g: Float
    val b: Float
    when (hueSection) {
        0 -> {
            r = chroma
            g = intermediateValue
            b = 0f
        }

        1 -> {
            r = intermediateValue
            g = chroma
            b = 0f
        }

        2 -> {
            r = 0f
            g = chroma
            b = intermediateValue
        }

        3 -> {
            r = 0f
            g = intermediateValue
            b = chroma
        }

        4 -> {
            r = intermediateValue
            g = 0f
            b = chroma
        }

        else -> {
            check(hueSection == 5)
            r = chroma
            g = 0f
            b = intermediateValue
        }
    }
    val lightnessAdjustment = lightness - chroma / 2

    return safeRgbColor(
        ((r + lightnessAdjustment) * 255f).toInt(),
        ((g + lightnessAdjustment) * 255f).toInt(),
        ((b + lightnessAdjustment) * 255f).toInt()
    )
}