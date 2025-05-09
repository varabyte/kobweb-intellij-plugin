// ElementColorProvider interface uses standard AWT Color, as no darkened version is needed
@file:Suppress("UseJBColor")

package com.varabyte.kobweb.intellij.colors

import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.varabyte.kobweb.intellij.util.kobweb.isInKobwebSource
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.psi.hasCustomGetter
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.idea.references.mainReference
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
private const val MAX_NUM_SEARCH_STEPS = 15

private val cssNamedColors: Map<String, Color> by lazy {
    mapOf(
        "transparent" to Color(0, 0, 0, 0),

        // From https://www.w3schools.com/colors/colors_names.asp
        "aliceblue" to Color(0xF0F8FF),
        "antiquewhite" to Color(0xFAEBD7),
        "aqua" to Color(0x00FFFF),
        "aquamarine" to Color(0x7FFFD4),
        "azure" to Color(0xF0FFFF),
        "beige" to Color(0xF5F5DC),
        "bisque" to Color(0xFFE4C4),
        "black" to Color(0x000000),
        "blanchedalmond" to Color(0xFFEBCD),
        "blue" to Color(0x0000FF),
        "blueviolet" to Color(0x8A2BE2),
        "brown" to Color(0xA52A2A),
        "burlywood" to Color(0xDEB887),
        "cadetblue" to Color(0x5F9EA0),
        "chartreuse" to Color(0x7FFF00),
        "chocolate" to Color(0xD2691E),
        "coral" to Color(0xFF7F50),
        "cornflowerblue" to Color(0x6495ED),
        "cornsilk" to Color(0xFFF8DC),
        "crimson" to Color(0xDC143C),
        "cyan" to Color(0x00FFFF),
        "darkblue" to Color(0x00008B),
        "darkcyan" to Color(0x008B8B),
        "darkgoldenrod" to Color(0xB8860B),
        "darkgray" to Color(0xA9A9A9),
        "darkgrey" to Color(0xA9A9A9),
        "darkgreen" to Color(0x006400),
        "darkkhaki" to Color(0xBDB76B),
        "darkmagenta" to Color(0x8B008B),
        "darkolivegreen" to Color(0x556B2F),
        "darkorange" to Color(0xFF8C00),
        "darkorchid" to Color(0x9932CC),
        "darkred" to Color(0x8B0000),
        "darksalmon" to Color(0xE9967A),
        "darkseagreen" to Color(0x8FBC8F),
        "darkslateblue" to Color(0x483D8B),
        "darkslategray" to Color(0x2F4F4F),
        "darkslategrey" to Color(0x2F4F4F),
        "darkturquoise" to Color(0x00CED1),
        "darkviolet" to Color(0x9400D3),
        "deeppink" to Color(0xFF1493),
        "deepskyblue" to Color(0x00BFFF),
        "dimgray" to Color(0x696969),
        "dimgrey" to Color(0x696969),
        "dodgerblue" to Color(0x1E90FF),
        "firebrick" to Color(0xB22222),
        "floralwhite" to Color(0xFFFAF0),
        "forestgreen" to Color(0x228B22),
        "fuchsia" to Color(0xFF00FF),
        "gainsboro" to Color(0xDCDCDC),
        "ghostwhite" to Color(0xF8F8FF),
        "gold" to Color(0xFFD700),
        "goldenrod" to Color(0xDAA520),
        "gray" to Color(0x808080),
        "grey" to Color(0x808080),
        "green" to Color(0x008000),
        "greenyellow" to Color(0xADFF2F),
        "honeydew" to Color(0xF0FFF0),
        "hotpink" to Color(0xFF69B4),
        "indianred" to Color(0xCD5C5C),
        "indigo" to Color(0x4B0082),
        "ivory" to Color(0xFFFFF0),
        "khaki" to Color(0xF0E68C),
        "lavender" to Color(0xE6E6FA),
        "lavenderblush" to Color(0xFFF0F5),
        "lawngreen" to Color(0x7CFC00),
        "lemonchiffon" to Color(0xFFFACD),
        "lightblue" to Color(0xADD8E6),
        "lightcoral" to Color(0xF08080),
        "lightcyan" to Color(0xE0FFFF),
        "lightgoldenrodyellow" to Color(0xFAFAD2),
        "lightgray" to Color(0xD3D3D3),
        "lightgrey" to Color(0xD3D3D3),
        "lightgreen" to Color(0x90EE90),
        "lightpink" to Color(0xFFB6C1),
        "lightsalmon" to Color(0xFFA07A),
        "lightseagreen" to Color(0x20B2AA),
        "lightskyblue" to Color(0x87CEFA),
        "lightslategray" to Color(0x778899),
        "lightslategrey" to Color(0x778899),
        "lightsteelblue" to Color(0xB0C4DE),
        "lightyellow" to Color(0xFFFFE0),
        "lime" to Color(0x00FF00),
        "limegreen" to Color(0x32CD32),
        "linen" to Color(0xFAF0E6),
        "magenta" to Color(0xFF00FF),
        "maroon" to Color(0x800000),
        "mediumaquamarine" to Color(0x66CDAA),
        "mediumblue" to Color(0x0000CD),
        "mediumorchid" to Color(0xBA55D3),
        "mediumpurple" to Color(0x9370DB),
        "mediumseagreen" to Color(0x3CB371),
        "mediumslateblue" to Color(0x7B68EE),
        "mediumspringgreen" to Color(0x00FA9A),
        "mediumturquoise" to Color(0x48D1CC),
        "mediumvioletred" to Color(0xC71585),
        "midnightblue" to Color(0x191970),
        "mintcream" to Color(0xF5FFFA),
        "mistyrose" to Color(0xFFE4E1),
        "moccasin" to Color(0xFFE4B5),
        "navajowhite" to Color(0xFFDEAD),
        "navy" to Color(0x000080),
        "oldlace" to Color(0xFDF5E6),
        "olive" to Color(0x808000),
        "olivedrab" to Color(0x6B8E23),
        "orange" to Color(0xFFA500),
        "orangered" to Color(0xFF4500),
        "orchid" to Color(0xDA70D6),
        "palegoldenrod" to Color(0xEEE8AA),
        "palegreen" to Color(0x98FB98),
        "paleturquoise" to Color(0xAFEEEE),
        "palevioletred" to Color(0xDB7093),
        "papayawhip" to Color(0xFFEFD5),
        "peachpuff" to Color(0xFFDAB9),
        "peru" to Color(0xCD853F),
        "pink" to Color(0xFFC0CB),
        "plum" to Color(0xDDA0DD),
        "powderblue" to Color(0xB0E0E6),
        "purple" to Color(0x800080),
        "rebeccapurple" to Color(0x663399),
        "red" to Color(0xFF0000),
        "rosybrown" to Color(0xBC8F8F),
        "royalblue" to Color(0x4169E1),
        "saddlebrown" to Color(0x8B4513),
        "salmon" to Color(0xFA8072),
        "sandybrown" to Color(0xF4A460),
        "seagreen" to Color(0x2E8B57),
        "seashell" to Color(0xFFF5EE),
        "sienna" to Color(0xA0522D),
        "silver" to Color(0xC0C0C0),
        "skyblue" to Color(0x87CEEB),
        "slateblue" to Color(0x6A5ACD),
        "slategray" to Color(0x708090),
        "slategrey" to Color(0x708090),
        "snow" to Color(0xFFFAFA),
        "springgreen" to Color(0x00FF7F),
        "steelblue" to Color(0x4682B4),
        "tan" to Color(0xD2B48C),
        "teal" to Color(0x008080),
        "thistle" to Color(0xD8BFD8),
        "tomato" to Color(0xFF6347),
        "turquoise" to Color(0x40E0D0),
        "violet" to Color(0xEE82EE),
        "wheat" to Color(0xF5DEB3),
        "white" to Color(0xFFFFFF),
        "whitesmoke" to Color(0xF5F5F5),
        "yellow" to Color(0xFFFF00),
        "yellowgreen" to Color(0x9ACD32),
    )
}

private val PsiElement.containingClassOrObject
    get(): KtClassOrObject? {
        var parent = this.parent
        while (parent != null) {
            if (parent is KtClassOrObject) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

private fun PsiElement.isComposeHtmlColor(): Boolean {
    return containingClassOrObject?.fqName?.asString() == "org.jetbrains.compose.web.css.Color"
}

private fun KtProperty.isKobwebColor(): Boolean {
    return containingClassOrObject?.fqName?.asString() == "com.varabyte.kobweb.compose.ui.graphics.Colors"
}

/**
 * Enables showing small rectangular gutter icons that preview Kobweb colors
 */
class KobwebColorProvider : ElementColorProvider {
    override fun getColorFrom(element: PsiElement): Color? {
        return when {
            element !is LeafPsiElement -> null
            element.elementType != KtTokens.IDENTIFIER -> null
            element.isComposeHtmlColor() -> {
                // If we're inside Compose HTML source, intercept and return the color directly. Adding a case for a
                // single file seems like overkill but it can happen quite often if a user navigates to a Compose HTML
                // color, e.g. `Color.red`.
                cssNamedColors[element.text]
            }
            element.parent is KtProperty -> null // Avoid showing multiple previews
            !element.isInReadableKobwebProject() && !element.isInKobwebSource() -> null
            else -> traceColor(element.parent) // Leaf is just text. The parent is the actual object
        }
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
 * Note that we only want to return an element as having a color IF...
 *
 * * It is a call to Color.rgb, hsl, or that family of methods (e.g. `Color.rgb(0xFF, 0x00, 0xFF)`.
 * * It is a property that references another property that is a color (e.g. `SiteColors.Accent` which may be set to
 *   `Colors.Violet`.)
 * * It is an instance of a Compose HTML or Kobweb color property (e.g. `Colors.AliceBlue`, `Color.aliceblue`).
 *
 * @return the color being referenced, or null if the [element] ultimately doesn't resolve to
 * a color at all (which is common) or if the amount of times we'd have to follow references to get to the color
 * is too many, or it *was* a color but not one we could extract specific information
 * about (e.g. a method that returns one of two colors based on a condition).
 */
private fun traceColor(element: PsiElement): Color? {
    val visitedElements = mutableSetOf<PsiElement>()
    var stepCount = 0

    // Process elements BFS style; otherwise, we could easily follow the wrong branch way too deep and use up our
    // allotted search step count. We don't really expect to find colors more than a couple levels deep.
    val elementQueue = ArrayDeque(listOf(element))
    while (elementQueue.isNotEmpty()) {
        val currentElement = elementQueue.removeFirst()
        if (!visitedElements.add(currentElement)) continue
        if (stepCount++ > MAX_NUM_SEARCH_STEPS) break

        when (currentElement) {
            is KtDotQualifiedExpression -> currentElement.selectorExpression

            is KtNameReferenceExpression -> when {
                currentElement.parent is KtCallExpression -> currentElement.parent // Element is name of a function
                else -> currentElement.findDeclaration()
            }

            is KtProperty -> {
                // If we encounter a Kobweb color (e.g. `Colors.AliceBlue`) or a Compose HTML one
                // (e.g. `Color.aliceblue`), then we can stop here and don't have to dive deep any further.
                if (currentElement.isKobwebColor()) {
                    cssNamedColors[currentElement.name?.lowercase().orEmpty()]?.let { return it }
                } else if (currentElement.isComposeHtmlColor()) {
                    cssNamedColors[currentElement.name]?.let { return it }
                }

                when {
                    currentElement.hasInitializer() -> currentElement.initializer
                    currentElement.hasCustomGetter() -> currentElement.getter
                    else -> null
                }
            }

            is KtPropertyAccessor -> {
                currentElement.bodyExpression
            }

            is KtCallExpression -> {
                val color = currentElement.tryParseKobwebColorFunctionColor()
                if (color != null) return color
                null
            }

            else -> null
        }?.let { nextElement -> elementQueue.add(nextElement) }
    }

    return null
}

private fun Float.toColorInt(): Int {
    return (this * 255f).roundToInt().coerceIn(0, 255)
}

private object ColorFunctions {
    private val KOBWEB_COLOR_COMPANION_ID =
        ClassId.fromString("com/varabyte/kobweb/compose/ui/graphics/Color.Companion")
    val rgb = CallableId(KOBWEB_COLOR_COMPANION_ID, Name.identifier("rgb"))
    val rgba = CallableId(KOBWEB_COLOR_COMPANION_ID, Name.identifier("rgba"))
    val argb = CallableId(KOBWEB_COLOR_COMPANION_ID, Name.identifier("argb"))
    val hsl = CallableId(KOBWEB_COLOR_COMPANION_ID, Name.identifier("hsl"))
    val hsla = CallableId(KOBWEB_COLOR_COMPANION_ID, Name.identifier("hsla"))

    val entries = listOf(rgb, rgba, argb, hsl, hsla)
    val names = entries.map { it.callableName }
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
    val ktExpression = (this.calleeExpression as? KtNameReferenceExpression)
        ?.takeIf { it.getReferencedNameAsName() in ColorFunctions.names }
        ?: return null
    analyze(ktExpression) {
        val callableId = (ktExpression.mainReference.resolveToSymbol() as? KaFunctionSymbol)
            ?.callableId
            ?.takeIf { it in ColorFunctions.entries }
            ?: return@analyze
        val functionArgs = ktExpression.resolveToCall()?.successfulFunctionCallOrNull()?.argumentMapping
            ?: return@analyze

        when (callableId) {
            ColorFunctions.rgb -> when (functionArgs.size) {
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

            ColorFunctions.rgba -> when (functionArgs.size) {
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

            ColorFunctions.argb -> when (functionArgs.size) {
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

            ColorFunctions.hsl -> {
                val h = functionArgs.entries.first().key.evaluate()
                    .let { it?.asIntOrNull() ?: it?.asFloatOrNull()?.roundToInt() }
                    ?: return@analyze
                val (s, l) = functionArgs.entries.drop(1)
                    .mapNotNull { it.key.evaluate()?.asFloatOrNull() }
                    .takeIf { it.size == 2 }
                    ?: return@analyze
                return tryCreateHslColor(h, s, l)
            }

            ColorFunctions.hsla -> {
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
