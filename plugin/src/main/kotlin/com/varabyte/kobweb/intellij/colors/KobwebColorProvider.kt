// ElementColorProvider interface uses standard AWT Color, as no darkened version is needed
@file:Suppress("UseJBColor")

package com.varabyte.kobweb.intellij.colors

import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.varabyte.kobweb.intellij.util.kobweb.isInKobwebSource
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.js.translate.declaration.hasCustomGetter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import java.awt.Color
import kotlin.math.abs

/**
 * This constant prevents the color tracer from following references ridiculously deep into the codebase.
 *
 * If a method ultimately returns a color, it's unlikely that it will involve *that* many jumps to fetch it. Testing
 * has showed that a search depth of 15 allows finding a result 3-4 references deep. If we don't limit this, we could
 * possibly get stuck chasing a cyclical loop.
 *
 * Also, high limits may increase memory usage because we have to chase down every method we come across as possibly
 * returning a color. Unlimited search depth might also introduce lag or, in the case of a cycle, a stack overflow.
 *
 * Note that Android Studio sets their depth a bit higher than we do. However, they also appear to do their tracing of
 * colors differently. If there are reports in the wild about color preview not working, we can consider increasing this
 * value at that time, though it is likely caused by their specific color function not being supported or the tracing
 * algorithm being unable to analyze more complex code correctly.
 *
 * @see <a href="https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:sdk-common/src/main/java/com/android/ide/common/resources/ResourceResolver.java;l=67?q=MAX_RESOURCE_INDIRECTION">Android Studio's ResourceResolver.java</a>
 */
private const val MAX_SEARCH_DEPTH = 15

private const val KOBWEB_COLOR_COMPANION_FQ_NAME = "com.varabyte.kobweb.compose.ui.graphics.Color.Companion"

/**
 * Enables showing small rectangular gutter icons that preview Kobweb colors
 */
class KobwebColorProvider : ElementColorProvider {

    override fun getColorFrom(element: PsiElement): Color? = when {
        element !is LeafPsiElement -> null
        element.elementType != KtTokens.IDENTIFIER -> null
        element.parent is KtProperty -> null // Avoid showing multiple previews
        !element.isInReadableKobwebProject() && !element.isInKobwebSource() -> null
        else -> traceColor(element.parent) // Leaf is just text. The parent is the actual object
    }

    // TODO(#30): Support setting colors when possible
    override fun setColorTo(element: PsiElement, color: Color) = Unit
}

/**
 * Tries resolving references as deep as possible and checks if a Kobweb color is being referred to.
 *
 * @return the color being referenced, or null if the [element] ultimately doesn't resolve to
 * a color at all (which is common) or if the amount of times we'd have to follow references to get to the color
 * is too many, or it *was* a color but not one we could extract specific information
 * about (e.g. a method that returns one of two colors based on a condition).
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

        is KtCallExpression -> null.also {
            val calleeExpression = element.calleeExpression as? KtNameReferenceExpression ?: return@also
            val callee = calleeExpression.findDeclaration() as? KtNamedFunction ?: return@also

            tryParseKobwebColorFunctionCall(callee, element.valueArguments)?.let { parsedColor ->
                return parsedColor
            }
        }

        else -> null
    }

    return if (currentDepth <= MAX_SEARCH_DEPTH) {
        nextElement?.let { traceColor(it, currentDepth + 1) }
    } else null
}

/**
 * Checks if a called function is a Kobweb color function and if it is, tries extracting the color from the call.
 *
 * @param callee The function being called, that might be a Kobweb color function
 * @param valueArguments The arguments the [callee] is called with
 *
 * @return The specified color, if it could be parsed and the callee is a Kobweb color function, otherwise null
 */
private fun tryParseKobwebColorFunctionCall(
    callee: KtNamedFunction,
    valueArguments: Collection<KtValueArgument>
): Color? = with(valueArguments) {
    when {
        callee.isKobwebColorFunction("rgb(r: Int, g: Int, b: Int)") ->
            evaluateArguments<Int>(3)?.let { args ->
                tryCreateRgbColor(args[0], args[1], args[2])
            }

        callee.isKobwebColorFunction("rgb(value: Int)") ->
            evaluateArguments<Int>(1)?.let { args ->
                tryCreateRgbColor(args[0])
            }

        callee.isKobwebColorFunction("rgba(value: Int)", "rgba(value: Long)") ->
            (evaluateArguments<Int>(1) ?: evaluateArguments<Long, Int>(1) { it.toInt() })?.let { args ->
                tryCreateRgbColor(args[0] shr Byte.SIZE_BITS)
            }

        callee.isKobwebColorFunction("argb(value: Int)", "argb(value: Long)") ->
            (evaluateArguments<Int>(1) ?: evaluateArguments<Long, Int>(1) { it.toInt() })?.let { args ->
                tryCreateRgbColor(args[0] and 0x00_FF_FF_FF)
            }

        callee.isKobwebColorFunction("hsl(h: Float, s: Float, l: Float)") ->
            evaluateArguments<Float>(3)?.let { args ->
                tryCreateHslColor(args[0], args[1], args[2])
            }

        callee.isKobwebColorFunction("hsla(h: Float, s: Float, l: Float, a: Float)") ->
            evaluateArguments<Float>(4)?.let { args ->
                tryCreateHslColor(args[0], args[1], args[2])
            }

        else -> null
    }
}


// navigationElement returns the element where a feature like "Go to declaration" would point:
// The source declaration, if found, and not a compiled one, which would make further analyzing impossible.
private fun KtSimpleNameExpression.findDeclaration(): PsiElement? = this.mainReference.resolve()?.navigationElement

/**
 * Evaluates a collection of value arguments to the specified type.
 *
 * For example, if we have a collection of decimal, hex, and binary arguments,
 * this method can parse them into regular integer values, so 123, 0x7B and 0b0111_1011
 * would all evaluate to 123.
 *
 * @param argCount The size the original and evaluated collections must have. If this value disagrees with the size of
 *   the passed in collection, it will throw an exception; it's essentially treated like an assertion at that point.
 *   Otherwise, it's used to avoid returning a final, evaluated array of unexpected size.
 * @param evaluatedValueMapper Convenience parameter to avoid having to type `.map { ... }.toTypedArray()`
 *
 * @return the evaluated arguments of length [argCount] if evaluation of **all** arguments succeeded,
 * and [argCount] elements were passed for evaluation, otherwise null
 */
private inline fun <reified Evaluated, reified Mapped> Collection<KtValueArgument>.evaluateArguments(
    argCount: Int,
    evaluatedValueMapper: (Evaluated) -> Mapped
): Array<Mapped>? {

    check(this.size == argCount) { "evaluateArguments called on a collection expecting $argCount arguments, but it only had ${this.size}"}

    val constantExpressions = this.mapNotNull { it.getArgumentExpression() as? KtConstantExpression }

    val evaluatedArguments = constantExpressions.mapNotNull {
        analyze(it.containingKtFile) {
            it.evaluate(KtConstantEvaluationMode.CONSTANT_LIKE_EXPRESSION_EVALUATION)?.value as? Evaluated
        }
    }

    return if (evaluatedArguments.size != argCount) null
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

private fun tryCreateRgbColor(r: Int, g: Int, b: Int) =
    runCatching { Color(r, g, b) }.getOrNull()

private fun tryCreateRgbColor(rgb: Int) =
    runCatching { Color(rgb) }.getOrNull()

private fun tryCreateHslColor(hue: Float, saturation: Float, lightness: Float): Color? {
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

    return tryCreateRgbColor(
        ((r + lightnessAdjustment) * 255f).toInt(),
        ((g + lightnessAdjustment) * 255f).toInt(),
        ((b + lightnessAdjustment) * 255f).toInt()
    )
}
