package com.varabyte.kobweb.intellij.util.kobweb.silk

import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import com.varabyte.kobweb.intellij.util.svg.SvgPathParser
import com.varabyte.kobweb.intellij.util.text.truncate
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import java.awt.BasicStroke
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

/** How many icons that just call another icon (e.g. `IndeterminateIcon` calling `MinusIcon`) to follow. */
private const val MAX_DELEGATION_DEPTH = 3

/**
 * Reads the shape this icon function draws, for functions whose body is a call to Silk's `createIcon` (e.g. Kobweb's
 * own `ArrowBackIcon`) or to Kobweb's `Svg`.
 *
 * This reads the source as written rather than running it, so it only understands the SVG elements, attributes, and
 * statements that Kobweb's icons use. Anything else fails the whole read, rather than leaving parts of the icon out.
 *
 * Strokes are converted into filled outlines, so the result can be painted like any other [IconShape].
 *
 * @throws IllegalArgumentException if the function's body can't be read, e.g. because it does something other than
 *   call `createIcon` or `Svg`, or uses code this doesn't understand.
 */
fun KtNamedFunction.readSvgIconShape(): IconShape {
    val name = name ?: unsupported(this, "an unnamed function")
    return readIconFunction(depth = 0).let { IconShape(name, it.width, it.path) }
}

/**
 * Reads the shape this call to Silk's `createIcon` or Kobweb's `Svg` draws.
 *
 * The shape is named after the function the call is in, if any. See the other [readSvgIconShape] for details.
 *
 * @throws IllegalArgumentException if this isn't a `createIcon` or `Svg` call, or uses code this doesn't understand.
 */
fun KtCallExpression.readSvgIconShape(): IconShape {
    val name = getStrictParentOfType<KtNamedFunction>()?.name.orEmpty()
    val shape = readIconRoot() ?: unsupported(this, "a call that isn't to `createIcon` or `Svg`")
    return IconShape(name, shape.width, shape.path)
}

private fun unsupported(element: PsiElement, description: String = "`${element.text.truncate(40)}`"): Nothing =
    throw IllegalArgumentException("Unsupported in icon: $description")

private class ViewBox(val x: Double, val y: Double, val width: Double, val height: Double)

/**
 * Presentation attributes that apply to an element, which it inherits from its groups.
 *
 * Previews are drawn in a single color, so all that matters about the fill and stroke is whether they're drawn, not
 * their color.
 *
 * The defaults are SVG's: https://www.w3.org/TR/SVG2/painting.html
 *
 * @property fillRule One of [Path2D]'s winding rules.
 */
private data class Presentation(
    val isFilled: Boolean = true,
    val isStroked: Boolean = false,
    val strokeWidth: Float = 1f,
    val fillRule: Int = Path2D.WIND_NON_ZERO,
    val lineCap: Int = BasicStroke.CAP_BUTT,
    val lineJoin: Int = BasicStroke.JOIN_MITER,
    val miterLimit: Float = 4f,
)

/**
 * What's in effect at some point of an icon's content.
 *
 * @property variables Loop variables, e.g. `y` inside `for (y in listOf(3, 12, 21))`.
 */
private data class Scope(val presentation: Presentation, val variables: Map<String, Double> = emptyMap())

private fun KtNamedFunction.readIconFunction(depth: Int): IconShape {
    val body = bodyBlockExpression?.statements?.singleOrNull() ?: bodyExpression
    val call = body as? KtCallExpression ?: unsupported(this, "a body that isn't a single call")
    val callee = call.calleeExpression as? KtNameReferenceExpression ?: unsupported(call)
    call.readIconRoot()?.let { return it }

    // An icon that's just another icon, e.g. `fun IndeterminateIcon(modifier: Modifier) = MinusIcon(modifier)`
    if (depth >= MAX_DELEGATION_DEPTH) unsupported(call, "icons that call other icons this deeply")
    val target = callee.mainReference.resolve()?.navigationElement as? KtNamedFunction ?: unsupported(call)
    return target.readIconFunction(depth + 1)
}

/**
 * Reads this call if it's one that draws an icon's SVG itself, i.e. a call to `createIcon` or `Svg`, or returns null
 * for any other call.
 */
private fun KtCallExpression.readIconRoot(): IconShape? = when (calleeExpression?.text) {
    "createIcon" -> readCreateIcon()
    "Svg" -> readSvg()
    else -> null
}

private fun KtCallExpression.readCreateIcon(): IconShape {
    val arguments = namedArguments(listOf("viewBox", "width", "renderStyle", "attrs", "content"))
    val viewBox = arguments["viewBox"]?.let { readViewBox(it) } ?: ViewBox(0.0, 0.0, 24.0, 24.0)
    val presentation = arguments["renderStyle"]?.let { readRenderStyle(it) }
        ?: Presentation(isFilled = false, isStroked = true)
    // Attributes like `modifier.toAttrs()` style the icon as a whole (e.g. its size and color) but don't change its
    // shape. Attributes set directly in a lambda could, so those aren't supported.
    arguments["attrs"]?.let { attrs ->
        val selector = (attrs as? KtDotQualifiedExpression)?.selectorExpression as? KtCallExpression
        if (selector?.calleeExpression?.text != "toAttrs") unsupported(attrs)
    }
    val content = arguments["content"] as? KtLambdaExpression ?: unsupported(this, "`createIcon` without content")
    return readContent(content, Scope(presentation)).toIconShape(viewBox)
}

/** Attributes of an `Svg` element that affect how it's laid out or announced, but not the shape it draws. */
private val SVG_ATTRIBUTES_WITHOUT_SHAPE = setOf("width", "height", "id", "classes", "style", "ariaHidden")

private fun KtCallExpression.readSvg(): IconShape {
    val arguments = namedArguments(listOf("attrs", "content"))
    val attributes = (arguments["attrs"] as? KtLambdaExpression ?: unsupported(this, "`Svg` without attrs"))
        .readAttributes()
    val viewBox = when (val call = attributes.remove("viewBox")) {
        // Without a view box, SVG draws in the element's own units
        null -> {
            val width = attributes["width"]?.let { readNumber(it.singleArgument()) }
            val height = attributes["height"]?.let { readNumber(it.singleArgument()) }
            if (width == null || height == null) unsupported(this, "`Svg` without `viewBox`, `width`, and `height`")
            ViewBox(0.0, 0.0, width, height)
        }

        else -> {
            val values = call.valueArguments.map { it.getArgumentExpression() ?: unsupported(call) }
            when (values.size) {
                1 -> readViewBox(values[0])
                4 -> values.map { readNumber(it) }.let { (x, y, width, height) -> ViewBox(x, y, width, height) }
                else -> unsupported(call)
            }
        }
    }
    val presentation = Scope(Presentation()).readPresentation(attributes)
    attributes.keys.firstOrNull { it !in SVG_ATTRIBUTES_WITHOUT_SHAPE }
        ?.let { unsupported(this, "the `$it` attribute") }
    val content = arguments["content"] as? KtLambdaExpression ?: unsupported(this, "`Svg` without content")
    return readContent(content, Scope(presentation)).toIconShape(viewBox)
}

/** Converts this outline to IconShape's coordinates, where the view box is 1 unit tall. */
private fun Area.toIconShape(viewBox: ViewBox): IconShape {
    val transform = AffineTransform.getScaleInstance(1 / viewBox.height, 1 / viewBox.height).apply {
        translate(-viewBox.x, -viewBox.y)
    }
    return IconShape("", (viewBox.width / viewBox.height).toFloat(), Path2D.Float(this, transform))
}

private fun readViewBox(expression: KtExpression): ViewBox {
    val call = expression.asCall("ViewBox")
    val numbers = call.valueArguments.map { readNumber(it.getArgumentExpression()) }
    return when {
        call.calleeExpression?.text == "ViewBox" && numbers.size == 4 ->
            ViewBox(numbers[0], numbers[1], numbers[2], numbers[3])

        call.calleeExpression?.text == "sized" && numbers.size in 1..2 ->
            ViewBox(0.0, 0.0, numbers[0], numbers.getOrElse(1) { numbers[0] })

        else -> unsupported(expression)
    }
}

/**
 * Reads `createIcon`'s `renderStyle` argument, `IconRenderStyle.Fill()` or `IconRenderStyle.Stroke()`, as the
 * presentation `createIcon` gives the icon's root.
 */
private fun readRenderStyle(expression: KtExpression): Presentation {
    val call = expression.asCall("IconRenderStyle")
    return when (call.calleeExpression?.text) {
        "Fill" -> Presentation(isFilled = true, isStroked = false)
            .also { if (call.valueArguments.isNotEmpty()) unsupported(expression) }

        "Stroke" -> {
            val strokeWidth = when (call.valueArguments.size) {
                0 -> 1f
                1 -> readNumber(call.valueArguments[0].getArgumentExpression()).toFloat()
                else -> unsupported(expression)
            }
            Presentation(isFilled = false, isStroked = true, strokeWidth = strokeWidth)
        }

        else -> unsupported(expression)
    }
}

/** Reads the statements of a content lambda, e.g. `createIcon`'s or a `Group`'s, returning what they draw. */
private fun readContent(content: KtLambdaExpression, scope: Scope): Area =
    readStatements(content.bodyExpression?.statements.orEmpty(), scope)

private fun readStatements(statements: List<KtExpression>, scope: Scope): Area = Area().apply {
    for (statement in statements) {
        when (statement) {
            is KtForExpression -> add(readForLoop(statement, scope))
            is KtCallExpression -> add(readElement(statement, scope))
            else -> unsupported(statement)
        }
    }
}

/** Reads a loop over literal values, e.g. `for (y in listOf(3, 12, 21)) { ... }`. */
private fun readForLoop(loop: KtForExpression, scope: Scope): Area = Area().apply {
    val variable = loop.loopParameter?.name ?: unsupported(loop)
    val range = loop.loopRange as? KtCallExpression ?: unsupported(loop)
    if (range.calleeExpression?.text != "listOf") unsupported(range)
    val values = range.valueArguments.map { readNumber(it.getArgumentExpression(), scope.variables) }
    val body = loop.body?.let { (it as? KtBlockExpression)?.statements ?: listOf(it) } ?: unsupported(loop)

    for (value in values) {
        add(readStatements(body, scope.copy(variables = scope.variables + (variable to value))))
    }
}

private fun readElement(call: KtCallExpression, scope: Scope): Area {
    val elementName = call.calleeExpression?.text
    if (elementName == "Group") {
        val arguments = call.namedArguments(listOf("attrs", "content"))
        val attributes = (arguments["attrs"] as? KtLambdaExpression)?.readAttributes() ?: mutableMapOf()
        val presentation = scope.readPresentation(attributes)
        if (attributes.isNotEmpty()) unsupported(call, "the `${attributes.keys.first()}` attribute on a group")
        val content = arguments["content"] as? KtLambdaExpression ?: unsupported(call)
        return readContent(content, scope.copy(presentation = presentation))
    }

    val attrs = call.namedArguments(listOf("attrs"))["attrs"] as? KtLambdaExpression ?: unsupported(call)
    val attributes = attrs.readAttributes()
    val presentation = scope.readPresentation(attributes)
    fun number(name: String) = attributes.remove(name)?.let { readNumber(it.singleArgument(), scope.variables) }

    val shape: Shape = when (elementName) {
        "Path" -> {
            val d = attributes.remove("d")?.singleArgument() ?: unsupported(call, "a path without `d`")
            SvgPathParser.parse(if (d is KtLambdaExpression) readPathData(d, scope) else readString(d))
        }

        "Circle" -> {
            val r = number("r") ?: 0.0
            Ellipse2D.Double((number("cx") ?: 0.0) - r, (number("cy") ?: 0.0) - r, 2 * r, 2 * r)
        }

        "Line" -> Line2D.Double(number("x1") ?: 0.0, number("y1") ?: 0.0, number("x2") ?: 0.0, number("y2") ?: 0.0)

        "Rect" -> {
            val x = number("x") ?: 0.0
            val y = number("y") ?: 0.0
            val width = number("width") ?: 0.0
            val height = number("height") ?: 0.0
            // If only one corner radius is set, SVG uses it for both
            val rx = number("rx")
            val ry = number("ry") ?: rx
            if (rx == null && ry == null) {
                Rectangle2D.Double(x, y, width, height)
            } else {
                val arcWidth = minOf(2 * (rx ?: ry!!), width)
                val arcHeight = minOf(2 * (ry ?: rx!!), height)
                RoundRectangle2D.Double(x, y, width, height, arcWidth, arcHeight)
            }
        }

        "Polyline" -> {
            val points = attributes.remove("points")?.valueArguments?.map { readPoint(it, scope) }
                ?: unsupported(call, "a polyline without `points`")
            Path2D.Double().apply {
                points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
            }
        }

        else -> unsupported(call)
    }
    if (attributes.isNotEmpty()) unsupported(call, "the `${attributes.keys.first()}` attribute")

    return Area().apply {
        if (presentation.isFilled) add(Area(Path2D.Double(shape).apply { windingRule = presentation.fillRule }))
        if (presentation.isStroked) {
            val stroke = with(presentation) { BasicStroke(strokeWidth, lineCap, lineJoin, miterLimit) }
            add(Area(stroke.createStrokedShape(shape)))
        }
    }
}

/**
 * Reads the attribute calls in an element's attrs lambda (e.g. `cx(12)` in `Circle { cx(12) }`), by name.
 *
 * Each attribute may only be set once, which is all Kobweb's icons need.
 */
private fun KtLambdaExpression.readAttributes(): MutableMap<String, KtCallExpression> {
    val attributes = mutableMapOf<String, KtCallExpression>()
    for (statement in bodyExpression?.statements.orEmpty()) {
        val call = statement as? KtCallExpression ?: unsupported(statement)
        val name = call.calleeExpression?.text ?: unsupported(call)
        if (attributes.put(name, call) != null) unsupported(call, "setting `$name` more than once")
    }
    return attributes
}

/**
 * Returns this scope's presentation with any presentation attributes, like `strokeLineCap(...)`, from [attributes]
 * applied (and removed from [attributes]).
 */
private fun Scope.readPresentation(attributes: MutableMap<String, KtCallExpression>): Presentation {
    var presentation = presentation
    // Any paint other than `None` (e.g. `CurrentColor`, a color, or a gradient) draws something
    attributes.remove("fill")?.let { call ->
        presentation = presentation.copy(isFilled = call.singleArgument().enumEntryName() != "None")
    }
    attributes.remove("stroke")?.let { call ->
        presentation = presentation.copy(isStroked = call.singleArgument().enumEntryName() != "None")
    }
    attributes.remove("strokeWidth")?.let { call ->
        presentation = presentation.copy(strokeWidth = readNumber(call.singleArgument(), variables).toFloat())
    }
    attributes.remove("fillRule")?.let { call ->
        val fillRule = when (call.singleArgument().enumEntryName()) {
            "NonZero" -> Path2D.WIND_NON_ZERO
            "EvenOdd" -> Path2D.WIND_EVEN_ODD
            else -> unsupported(call)
        }
        presentation = presentation.copy(fillRule = fillRule)
    }
    attributes.remove("strokeLineCap")?.let { call ->
        val lineCap = when (call.singleArgument().enumEntryName()) {
            "Butt" -> BasicStroke.CAP_BUTT
            "Round" -> BasicStroke.CAP_ROUND
            "Square" -> BasicStroke.CAP_SQUARE
            else -> unsupported(call)
        }
        presentation = presentation.copy(lineCap = lineCap)
    }
    attributes.remove("strokeLineJoin")?.let { call ->
        val lineJoin = when (call.singleArgument().enumEntryName()) {
            "Miter" -> BasicStroke.JOIN_MITER
            "Round" -> BasicStroke.JOIN_ROUND
            "Bevel" -> BasicStroke.JOIN_BEVEL
            else -> unsupported(call)
        }
        presentation = presentation.copy(lineJoin = lineJoin)
    }
    attributes.remove("strokeMiterLimit")?.let { call ->
        presentation = presentation.copy(miterLimit = readNumber(call.singleArgument(), variables).toFloat())
    }
    return presentation
}

/**
 * Converts a path data builder (e.g. `d { moveTo(20, 11); lineTo(5.59, -5.59, isRelative = true) }`) into SVG path
 * data, the same way Kobweb's `SVGPathDataScope` does: each call adds one command.
 */
private fun readPathData(builder: KtLambdaExpression, scope: Scope): String {
    class Command(val letter: Char, val numberCount: Int, val canBeRelative: Boolean = true)

    val commands = mapOf(
        "moveTo" to Command('M', 2, canBeRelative = false),
        "lineTo" to Command('L', 2),
        "horizontalLineTo" to Command('H', 1),
        "verticalLineTo" to Command('V', 1),
        "curveTo" to Command('C', 6),
        "smoothCurveTo" to Command('S', 4),
        "quadraticBezierCurve" to Command('Q', 4),
        "smoothQuadraticBezierCurve" to Command('T', 2),
        "ellipticalArc" to Command('A', 7),
        "closePath" to Command('Z', 0, canBeRelative = false),
    )
    return builder.bodyExpression?.statements.orEmpty().joinToString(" ") { statement ->
        val call = statement as? KtCallExpression ?: unsupported(statement)
        val command = commands[call.calleeExpression?.text] ?: unsupported(call)
        val arguments = call.valueArguments
        val numbers = arguments.take(command.numberCount)
        if (numbers.size < command.numberCount || numbers.any { it.isNamed() }) unsupported(call)
        if (arguments.size > command.numberCount + 1) unsupported(call)
        val isRelative = arguments.getOrNull(command.numberCount)?.let { flag ->
            val flagName = flag.getArgumentName()?.asName?.asString()
            if (!command.canBeRelative || (flagName != null && flagName != "isRelative")) unsupported(call)
            readBoolean(flag.getArgumentExpression())
        } ?: false

        val letter = if (isRelative) command.letter.lowercaseChar() else command.letter
        val values = numbers.map { readNumber(it.getArgumentExpression(), scope.variables).toSvgNumber() }
        (listOf(letter.toString()) + values).joinToString(" ")
    }
}

/** Formats a number for path data, writing whole numbers without a decimal point (as arc flags must be). */
private fun Double.toSvgNumber() = if (this == toLong().toDouble()) toLong().toString() else toString()

/** Reads a point written as a pair, e.g. `3 to 12` in `points(3 to 12, 9 to 18)`. */
private fun readPoint(argument: KtValueArgument, scope: Scope): Pair<Double, Double> {
    val pair = argument.getArgumentExpression() as? KtBinaryExpression ?: unsupported(argument)
    if (pair.operationReference.getReferencedName() != "to") unsupported(pair)
    return readNumber(pair.left, scope.variables) to readNumber(pair.right, scope.variables)
}

/** @param variables Values for any names the number may refer to, e.g. loop variables. */
private fun readNumber(expression: KtExpression?, variables: Map<String, Double> = emptyMap()): Double =
    when (expression) {
        is KtConstantExpression -> {
            // Drop Kotlin's type suffixes and digit separators, e.g. `1.5f` or `1_000L`
            val text = expression.text.replace("_", "").trimEnd('f', 'F', 'L', 'u', 'U')
            text.toDoubleOrNull() ?: unsupported(expression)
        }

        is KtPrefixExpression -> when (expression.operationToken) {
            KtTokens.MINUS -> -readNumber(expression.baseExpression, variables)
            KtTokens.PLUS -> readNumber(expression.baseExpression, variables)
            else -> unsupported(expression)
        }

        is KtParenthesizedExpression -> readNumber(expression.expression, variables)
        is KtNameReferenceExpression -> variables[expression.getReferencedName()] ?: unsupported(expression)
        null -> throw IllegalArgumentException("Unsupported in icon: a missing number")
        else -> unsupported(expression)
    }

private fun readBoolean(expression: KtExpression?): Boolean = when (expression?.text) {
    "true" -> true
    "false" -> false
    else -> unsupported(expression ?: throw IllegalArgumentException("Unsupported in icon: a missing boolean"))
}

/** Reads a string literal, or literals joined with `+`, e.g. the path data in `d("M19.14 12.94" + "a.5.5")`. */
private fun readString(expression: KtExpression): String = when (expression) {
    is KtStringTemplateExpression -> expression.entries.joinToString("") { entry ->
        when (entry) {
            is KtLiteralStringTemplateEntry -> entry.text
            is KtEscapeStringTemplateEntry -> entry.unescapedValue
            else -> unsupported(entry)
        }
    }

    is KtBinaryExpression -> {
        if (expression.operationToken != KtTokens.PLUS) unsupported(expression)
        val left = expression.left ?: unsupported(expression)
        val right = expression.right ?: unsupported(expression)
        readString(left) + readString(right)
    }

    is KtParenthesizedExpression -> readString(expression.expression ?: unsupported(expression))
    else -> unsupported(expression)
}

/**
 * Returns this as a call, which may be qualified by [receiver] (e.g. both `Fill()` and `IconRenderStyle.Fill()` are OK
 * for an `IconRenderStyle` receiver).
 */
private fun KtExpression.asCall(receiver: String): KtCallExpression = when (this) {
    is KtCallExpression -> this
    is KtDotQualifiedExpression -> {
        if (receiverExpression.text != receiver) unsupported(this)
        selectorExpression as? KtCallExpression ?: unsupported(this)
    }

    else -> unsupported(this)
}

/** The name of the enum entry this refers to, e.g. "Round" for `SVGStrokeLineCap.Round`. */
private fun KtExpression.enumEntryName(): String =
    ((this as? KtDotQualifiedExpression)?.selectorExpression ?: this).text

/** The expression passed to this call's only argument, which may be a trailing lambda. */
private fun KtCallExpression.singleArgument(): KtExpression =
    valueArguments.singleOrNull()?.getArgumentExpression() ?: unsupported(this)

/**
 * Maps this call's arguments to their parameter names, given the called function's [parameterNames] in order. A
 * trailing lambda goes to the last parameter.
 */
private fun KtCallExpression.namedArguments(parameterNames: List<String>): Map<String, KtExpression> {
    val arguments = mutableMapOf<String, KtExpression>()
    valueArguments.forEachIndexed { i, argument ->
        val name = argument.getArgumentName()?.asName?.asString()
            ?: if (argument is KtLambdaArgument) parameterNames.last() else parameterNames.getOrNull(i)
            ?: unsupported(this)
        if (name !in parameterNames) unsupported(this, "the `$name` argument")
        arguments[name] = argument.getArgumentExpression() ?: unsupported(this)
    }
    return arguments
}
