// ElementColorProvider interface uses standard AWT Color, as no darkened version is needed
@file:Suppress("UseJBColor")

package com.varabyte.kobweb.intellij.colors

import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.varabyte.kobweb.intellij.util.kobweb.isInKobwebSource
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.js.translate.declaration.hasCustomGetter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
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

private object ColorFunctionIds {
    private val KOBWEB_COLOR_COMPANION_ID =
        ClassId.fromString("com/varabyte/kobweb/compose/ui/graphics/Color.Companion")
    val rgb = CallableId(KOBWEB_COLOR_COMPANION_ID, Name.identifier("rgb"))
    val rgba = CallableId(KOBWEB_COLOR_COMPANION_ID, Name.identifier("rgba"))
    val argb = CallableId(KOBWEB_COLOR_COMPANION_ID, Name.identifier("argb"))
    val hsl = CallableId(KOBWEB_COLOR_COMPANION_ID, Name.identifier("hsl"))
    val hsla = CallableId(KOBWEB_COLOR_COMPANION_ID, Name.identifier("hsla"))

    val entries = listOf(rgb, rgba, argb, hsl, hsla)
}

private fun KaConstantValue.asIntOrNull(): Int? = (this as? KaConstantValue.IntValue)?.value
private fun KaConstantValue.asFloatOrNull(): Float? = (this as? KaConstantValue.FloatValue)?.value
private fun KaConstantValue.asLongOrNull(): Long? = (this as? KaConstantValue.LongValue)?.value

/**
 * Checks if a call expression represents a Kobweb color function call and if so, try extracting the color from it.
 *
 * @return The specified color, if it could be parsed and the callee is a Kobweb color function, otherwise null
 */
private fun KtCallExpression.tryParseKobwebColorFunctionColor(): Color? {
    val ktExpression = this.calleeExpression ?: return null
    analyze(ktExpression) {
        val callableId = (ktExpression.mainReference?.resolveToSymbol() as? KaFunctionSymbol)
            ?.callableId
            ?.takeIf { it in ColorFunctionIds.entries }
            ?: return@analyze
        val functionArgs = ktExpression.resolveToCall()?.successfulFunctionCallOrNull()?.argumentMapping
            ?: return@analyze

        when (callableId) {
            ColorFunctionIds.rgb -> when (functionArgs.size) {
                1 -> {
                    val constantValue = functionArgs.entries.single().key.evaluate()
                    val rgb = constantValue?.asIntOrNull() ?: constantValue?.asLongOrNull()?.toInt() ?: return@analyze
                    return tryCreateRgbColor(rgb)
                }

                3 -> {
                    val (r, g, b) = functionArgs.mapNotNull {
                        val constantValue = it.key.evaluate()
                        constantValue?.asIntOrNull() ?: constantValue?.asFloatOrNull()?.toColorInt()
                    }.takeIf { it.size == 3 } ?: return@analyze
                    return tryCreateRgbColor(r, g, b)
                }
            }

            ColorFunctionIds.rgba -> when (functionArgs.size) {
                1 -> {
                    val constantValue = functionArgs.entries.single().key.evaluate()
                    val rgb = constantValue?.asIntOrNull() ?: constantValue?.asLongOrNull()?.toInt() ?: return@analyze
                    return tryCreateRgbColor(rgb shr 8)
                }

                4 -> {
                    val (r, g, b) = functionArgs.entries.take(3).mapNotNull {
                        val constantValue = it.key.evaluate()
                        constantValue?.asIntOrNull() ?: constantValue?.asFloatOrNull()?.toColorInt()
                    }.takeIf { it.size == 3 } ?: return@analyze
                    return tryCreateRgbColor(r, g, b)
                }
            }

            ColorFunctionIds.argb -> when (functionArgs.size) {
                1 -> {
                    val constantValue = functionArgs.entries.single().key.evaluate()
                    val rgb = constantValue?.asIntOrNull() ?: constantValue?.asLongOrNull()?.toInt() ?: return@analyze
                    return tryCreateRgbColor(rgb and 0x00_FF_FF_FF)
                }

                4 -> {
                    val (r, g, b) = functionArgs.entries.drop(1).mapNotNull {
                        val constantValue = it.key.evaluate()
                        constantValue?.asIntOrNull() ?: constantValue?.asFloatOrNull()?.toColorInt()
                    }.takeIf { it.size == 3 } ?: return@analyze
                    return tryCreateRgbColor(r, g, b)
                }
            }

            ColorFunctionIds.hsl -> {
                val h = functionArgs.entries.first().key.evaluate()
                    .let { it?.asIntOrNull() ?: it?.asFloatOrNull()?.roundToInt() }
                    ?: return@analyze
                val (s, l) = functionArgs.entries.drop(1)
                    .mapNotNull { it.key.evaluate()?.asFloatOrNull() }
                    .takeIf { it.size == 2 }
                    ?: return@analyze
                return tryCreateHslColor(h, s, l)
            }

            ColorFunctionIds.hsla -> {
                val h = functionArgs.entries.first().key.evaluate()
                    .let { it?.asIntOrNull() ?: it?.asFloatOrNull()?.roundToInt() }
                    ?: return@analyze
                val (s, l) = functionArgs.entries.drop(1).dropLast(1)
                    .mapNotNull { it.key.evaluate()?.asFloatOrNull() }
                    .takeIf { it.size == 2 }
                    ?: return@analyze
                return tryCreateHslColor(h, s, l)
            }
        }
    }
    return null
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
