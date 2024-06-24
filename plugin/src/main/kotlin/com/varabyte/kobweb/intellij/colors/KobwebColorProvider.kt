// ElementColorProvider interface uses standard AWT Color, as no darkened version is needed
@file:Suppress("UseJBColor")

package com.varabyte.kobweb.intellij.colors

import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.varabyte.kobweb.intellij.util.kobweb.isInKobwebSource
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.js.translate.declaration.hasCustomGetter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import java.awt.Color
import kotlin.math.abs
import kotlin.math.roundToInt

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

// navigationElement returns the element where a feature like "Go to declaration" would point:
// The source declaration, if found, and not a compiled one, which would make further analyzing impossible.
private fun KtSimpleNameExpression.findDeclaration(): PsiElement? = this.mainReference.resolve()?.navigationElement

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

        is KtCallExpression -> {
            val color = element.tryParseKobwebColorFunctionColor()
            if (color != null) return color
            null
        }

        else -> null
    }

    return if (currentDepth <= MAX_SEARCH_DEPTH) {
        nextElement?.let { traceColor(it, currentDepth + 1) }
    } else null
}

private fun Float.toColorInt(): Int {
    return (this * 255f).roundToInt().coerceIn(0, 255)
}

/**
 * Checks if a call expression represents a Kobweb color function call and if so, try extracting the color from it.
 *
 * @return The specified color, if it could be parsed and the callee is a Kobweb color function, otherwise null
 */
private fun KtCallExpression.tryParseKobwebColorFunctionColor(): Color? {
    "$KOBWEB_COLOR_COMPANION_FQ_NAME.rgb".let { rgbFqn ->
        this.extractConstantArguments1<Int>(rgbFqn)?.let { (rgb) ->
            return tryCreateRgbColor(rgb)
        }

        this.extractConstantArguments1<Long>(rgbFqn)?.let { (rgb) ->
            return tryCreateRgbColor(rgb.toInt())
        }

        this.extractConstantArguments3<Int, Int, Int>(rgbFqn)?.let { (r, g, b) ->
            return tryCreateRgbColor(r, g, b)
        }

        this.extractConstantArguments3<Float, Float, Float>(rgbFqn)?.let { (r, g, b) ->
            return tryCreateRgbColor(r.toColorInt(), g.toColorInt(), b.toColorInt())
        }
    }

    "$KOBWEB_COLOR_COMPANION_FQ_NAME.rgba".let { rgbaFqn ->
        this.extractConstantArguments1<Int>(rgbaFqn)?.let { (rgb) ->
            return tryCreateRgbColor(rgb shr 8)
        }

        this.extractConstantArguments1<Long>(rgbaFqn)?.let { (rgb) ->
            return tryCreateRgbColor(rgb.toInt() shr 8)
        }

        this.extractConstantArguments4<Int, Int, Int, Int>(rgbaFqn)?.let { (r, g, b) ->
            return tryCreateRgbColor(r, g, b)
        }

        this.extractConstantArguments4<Int, Int, Int, Float>(rgbaFqn)?.let { (r, g, b) ->
            return tryCreateRgbColor(r, g, b)
        }

        this.extractConstantArguments4<Float, Float, Float, Float>(rgbaFqn)?.let { (r, g, b) ->
            return tryCreateRgbColor(r.toColorInt(), g.toColorInt(), b.toColorInt())
        }
    }

    "$KOBWEB_COLOR_COMPANION_FQ_NAME.argb".let { argbFqn ->
        this.extractConstantArguments1<Int>(argbFqn)?.let { (rgb) ->
            return tryCreateRgbColor(rgb and 0x00_FF_FF_FF)
        }

        this.extractConstantArguments1<Long>(argbFqn)?.let { (rgb) ->
            return tryCreateRgbColor(rgb.toInt() and 0x00_FF_FF_FF)
        }

        this.extractConstantArguments4<Int, Int, Int, Int>(argbFqn)?.let { (_, r, g, b) ->
            return tryCreateRgbColor(r, g, b)
        }

        this.extractConstantArguments4<Float, Int, Int, Int>(argbFqn)?.let { (_, r, g, b) ->
            return tryCreateRgbColor(r, g, b)
        }

        this.extractConstantArguments4<Float, Float, Float, Float>(argbFqn)?.let { (_, r, g, b) ->
            return tryCreateRgbColor(r.toColorInt(), g.toColorInt(), b.toColorInt())
        }
    }

    "$KOBWEB_COLOR_COMPANION_FQ_NAME.hsl".let { hslFqn ->
        this.extractConstantArguments3<Int, Float, Float>(hslFqn)?.let { (h, s, l) ->
            return tryCreateHslColor(h, s, l)
        }

        this.extractConstantArguments3<Float, Float, Float>(hslFqn)?.let { (h, s, l) ->
            return tryCreateHslColor(h.roundToInt(), s, l)
        }
    }

    "$KOBWEB_COLOR_COMPANION_FQ_NAME.hsla".let { hslaFqn ->
        this.extractConstantArguments4<Int, Float, Float, Float>(hslaFqn)?.let { (h, s, l) ->
            return tryCreateHslColor(h, s, l)
        }

        this.extractConstantArguments4<Float, Float, Float, Float>(hslaFqn)?.let { (h, s, l) ->
            return tryCreateHslColor(h.roundToInt(), s, l)
        }
    }

    return null
}

private data class Values<T1, T2, T3, T4>(
    val v1: T1,
    val v2: T2,
    val v3: T3,
    val v4: T4
)

private inline fun <reified T> KtValueArgument.extractConstantValue(): T? {
    val constantExpression = getArgumentExpression() as? KtConstantExpression ?: return null
    val bindingContext = constantExpression.analyze(BodyResolveMode.PARTIAL)
    val constant = bindingContext.get(BindingContext.COMPILE_TIME_VALUE, constantExpression) ?: return null
    val type = bindingContext.getType(constantExpression) ?: return null
    return constant.getValue(type) as? T
}

private fun KtCallExpression.valueArgumentsIf(fqn: String, requiredSize: Int): List<KtValueArgument>? {
    val calleeExpression = calleeExpression as? KtNameReferenceExpression ?: return null
    val callee = calleeExpression.findDeclaration() as? KtNamedFunction ?: return null
    if (callee.kotlinFqName?.asString() != fqn) return null
    return valueArguments.takeIf { it.size == requiredSize }
}

private inline fun <reified I> KtCallExpression.extractConstantArguments1(fqn: String): Values<I, Unit, Unit, Unit>? {
    val valueArguments = valueArgumentsIf(fqn, 1) ?: return null
    val v1: I? = valueArguments[0].extractConstantValue()
    return if (v1 != null) Values(v1, Unit, Unit, Unit) else null
}

private inline fun <reified I1, reified I2, reified I3> KtCallExpression.extractConstantArguments3(fqn: String): Values<I1, I2, I3, Unit>? {
    val valueArguments = valueArgumentsIf(fqn, 3) ?: return null
    val v1: I1? = valueArguments[0].extractConstantValue()
    val v2: I2? = valueArguments[1].extractConstantValue()
    val v3: I3? = valueArguments[2].extractConstantValue()
    return if (v1 != null && v2 != null && v3 != null) Values(v1, v2, v3, Unit) else null
}

private inline fun <reified I1, reified I2, reified I3, reified I4> KtCallExpression.extractConstantArguments4(fqn: String): Values<I1, I2, I3, I4>? {
    val valueArguments = valueArgumentsIf(fqn, 4) ?: return null
    val v1: I1? = valueArguments[0].extractConstantValue()
    val v2: I2? = valueArguments[1].extractConstantValue()
    val v3: I3? = valueArguments[2].extractConstantValue()
    val v4: I4? = valueArguments[3].extractConstantValue()
    return if (v1 != null && v2 != null && v3 != null && v4 != null) Values(v1, v2, v3, v4) else null
}

private fun tryCreateRgbColor(r: Int, g: Int, b: Int) =
    runCatching { Color(r, g, b) }.getOrNull()

private fun tryCreateRgbColor(rgb: Int) =
    runCatching { Color(rgb) }.getOrNull()

// Expected values:
//   hue: 0-360
//   saturation: 0-1
//   lightness: 0-1
private fun tryCreateHslColor(hue: Int, saturation: Float, lightness: Float): Color? {
    // https://en.wikipedia.org/wiki/HSL_and_HSV#Color_conversion_formulae
    val chroma = (1 - abs(2 * lightness - 1)) * saturation
    val intermediateValue = chroma * (1 - abs(((hue / 60) % 2) - 1))
    val hueSection = (hue % 360) / 60
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
