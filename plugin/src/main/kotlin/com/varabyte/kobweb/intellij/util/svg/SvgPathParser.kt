package com.varabyte.kobweb.intellij.util.svg

import java.awt.geom.Path2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Parses SVG path data (the value of a `<path>` element's `d` attribute) into a Java2D path.
 *
 * Follows the path grammar and arc implementation notes of the SVG 2 spec:
 * https://www.w3.org/TR/SVG2/paths.html#PathDataBNF and https://www.w3.org/TR/SVG2/implnote.html#ArcImplementationNotes
 *
 * Arcs are approximated with cubic Bézier curves, since Java2D has no rotated elliptical arc segment.
 *
 * This is written by hand because there's no good library to use instead. Apache Batik's parser (which the icon
 * previews generator uses) would add several hundred KB of jars to the plugin, and the JSVG library that IntelliJ
 * bundles isn't a plugin API: in some IntelliJ releases, its parser classes are stripped out.
 */
class SvgPathParser private constructor(private val data: String) {
    companion object {
        /**
         * @throws IllegalArgumentException if [data] isn't valid path data.
         */
        fun parse(data: String): Path2D.Double = SvgPathParser(data).parse()
    }

    private val path = Path2D.Double()
    private var pos = 0

    // The current point, the start of the current subpath (where "Z" returns to), and the last control point of the
    // previous command, if it was a curve that the smooth curve commands ("S" and "T") can reflect
    private var x = 0.0
    private var y = 0.0
    private var startX = 0.0
    private var startY = 0.0
    private var lastCubicControl: Pair<Double, Double>? = null
    private var lastQuadControl: Pair<Double, Double>? = null

    private fun parse(): Path2D.Double {
        skipSeparators()
        if (pos == data.length) return path
        var command = data[pos]
        require(command == 'M' || command == 'm') { "Path data must start with a move command: \"$data\"" }
        while (pos < data.length) {
            val c = data[pos]
            if (c.isLetter()) {
                command = c
                pos++
            } else {
                // Repeated arguments without a new letter repeat the command, except "M"/"m" repeat as "L"/"l"
                require(command != 'Z' && command != 'z') { "Unexpected number after closing a path at $pos" }
                if (command == 'M') command = 'L' else if (command == 'm') command = 'l'
            }
            runCommand(command)
            skipSeparators()
        }
        return path
    }

    private fun runCommand(command: Char) {
        val isRelative = command.isLowerCase()
        val dx = if (isRelative) x else 0.0
        val dy = if (isRelative) y else 0.0
        var cubicControl: Pair<Double, Double>? = null
        var quadControl: Pair<Double, Double>? = null
        when (command.uppercaseChar()) {
            'M' -> {
                x = number() + dx
                y = number() + dy
                startX = x
                startY = y
                path.moveTo(x, y)
            }

            'L' -> lineTo(number() + dx, number() + dy)
            'H' -> lineTo(number() + dx, y)
            'V' -> lineTo(x, number() + dy)
            'C' -> {
                val x1 = number() + dx
                val y1 = number() + dy
                val x2 = number() + dx
                val y2 = number() + dy
                cubicControl = x2 to y2
                curveTo(x1, y1, x2, y2, number() + dx, number() + dy)
            }

            'S' -> {
                val (x1, y1) = reflect(lastCubicControl)
                val x2 = number() + dx
                val y2 = number() + dy
                cubicControl = x2 to y2
                curveTo(x1, y1, x2, y2, number() + dx, number() + dy)
            }

            'Q' -> {
                val x1 = number() + dx
                val y1 = number() + dy
                quadControl = x1 to y1
                quadTo(x1, y1, number() + dx, number() + dy)
            }

            'T' -> {
                val (x1, y1) = reflect(lastQuadControl)
                quadControl = x1 to y1
                quadTo(x1, y1, number() + dx, number() + dy)
            }

            'A' -> {
                val rx = number()
                val ry = number()
                val rotation = number()
                val largeArc = flag()
                val sweep = flag()
                arcTo(rx, ry, rotation, largeArc, sweep, number() + dx, number() + dy)
            }

            'Z' -> {
                path.closePath()
                x = startX
                y = startY
            }

            else -> throw IllegalArgumentException("Unknown path command '$command' at ${pos - 1}")
        }
        lastCubicControl = cubicControl
        lastQuadControl = quadControl
    }

    private fun lineTo(x: Double, y: Double) {
        path.lineTo(x, y)
        this.x = x
        this.y = y
    }

    private fun curveTo(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double) {
        path.curveTo(x1, y1, x2, y2, x, y)
        this.x = x
        this.y = y
    }

    private fun quadTo(x1: Double, y1: Double, x: Double, y: Double) {
        path.quadTo(x1, y1, x, y)
        this.x = x
        this.y = y
    }

    /** The reflection of [control] about the current point, or the current point itself if there is no [control]. */
    private fun reflect(control: Pair<Double, Double>?): Pair<Double, Double> =
        control?.let { (cx, cy) -> (2 * x - cx) to (2 * y - cy) } ?: (x to y)

    private fun arcTo(
        rx: Double,
        ry: Double,
        rotationDegrees: Double,
        largeArc: Boolean,
        sweep: Boolean,
        x: Double,
        y: Double,
    ) {
        val x1 = this.x
        val y1 = this.y
        if (x1 == x && y1 == y) return
        if (rx == 0.0 || ry == 0.0) {
            lineTo(x, y)
            return
        }

        // Convert from endpoint to center parameterization (SVG 2 implementation notes, B.2.4 and B.2.5)
        val phi = rotationDegrees * PI / 180
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val halfDx = (x1 - x) / 2
        val halfDy = (y1 - y) / 2
        val x1p = cosPhi * halfDx + sinPhi * halfDy
        val y1p = -sinPhi * halfDx + cosPhi * halfDy

        var rxa = abs(rx)
        var rya = abs(ry)
        val lambda = (x1p * x1p) / (rxa * rxa) + (y1p * y1p) / (rya * rya)
        if (lambda > 1) {
            rxa *= sqrt(lambda)
            rya *= sqrt(lambda)
        }

        val numerator = rxa * rxa * rya * rya - rxa * rxa * y1p * y1p - rya * rya * x1p * x1p
        val denominator = rxa * rxa * y1p * y1p + rya * rya * x1p * x1p
        val coef = (if (largeArc != sweep) 1 else -1) * sqrt(maxOf(0.0, numerator / denominator))
        val cxp = coef * rxa * y1p / rya
        val cyp = coef * -rya * x1p / rxa
        val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x) / 2
        val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y) / 2

        fun angle(ux: Double, uy: Double, vx: Double, vy: Double) = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        val theta1 = angle(1.0, 0.0, (x1p - cxp) / rxa, (y1p - cyp) / rya)
        var deltaTheta = angle((x1p - cxp) / rxa, (y1p - cyp) / rya, (-x1p - cxp) / rxa, (-y1p - cyp) / rya)
        if (!sweep && deltaTheta > 0) deltaTheta -= 2 * PI
        if (sweep && deltaTheta < 0) deltaTheta += 2 * PI

        // Approximate the arc with one cubic curve per quarter turn (or less), using the standard 4/3 tan(θ/4) handle
        // length. A point on the ellipse at angle t is center + R(phi) * (rx cos t, ry sin t).
        val segmentCount = ceil(abs(deltaTheta) / (PI / 2)).toInt()
        val segmentAngle = deltaTheta / segmentCount
        val handle = 4.0 / 3 * tan(segmentAngle / 4)
        fun pointX(t: Double) = cx + rxa * cosPhi * cos(t) - rya * sinPhi * sin(t)
        fun pointY(t: Double) = cy + rxa * sinPhi * cos(t) + rya * cosPhi * sin(t)
        fun tangentX(t: Double) = -rxa * cosPhi * sin(t) - rya * sinPhi * cos(t)
        fun tangentY(t: Double) = -rxa * sinPhi * sin(t) + rya * cosPhi * cos(t)
        var t1 = theta1
        repeat(segmentCount) { i ->
            val t2 = t1 + segmentAngle
            // Land exactly on the requested endpoint, rather than on a point computed with rounding error
            val endX = if (i == segmentCount - 1) x else pointX(t2)
            val endY = if (i == segmentCount - 1) y else pointY(t2)
            path.curveTo(
                pointX(t1) + handle * tangentX(t1), pointY(t1) + handle * tangentY(t1),
                endX - handle * tangentX(t2), endY - handle * tangentY(t2),
                endX, endY,
            )
            t1 = t2
        }
        this.x = x
        this.y = y
    }

    private fun skipSeparators() {
        while (pos < data.length && (data[pos].isWhitespace() || data[pos] == ',')) pos++
    }

    private fun number(): Double {
        skipSeparators()
        val start = pos
        if (pos < data.length && (data[pos] == '+' || data[pos] == '-')) pos++
        val digitsStart = pos
        while (pos < data.length && data[pos].isDigit()) pos++
        // A second "." starts the next number, e.g. "0.5.5" is 0.5 and .5
        if (pos < data.length && data[pos] == '.') {
            pos++
            while (pos < data.length && data[pos].isDigit()) pos++
        }
        require(pos > digitsStart && data.substring(digitsStart, pos) != ".") { "Expected a number at $start: \"$data\"" }
        if (pos < data.length && (data[pos] == 'e' || data[pos] == 'E')) {
            pos++
            if (pos < data.length && (data[pos] == '+' || data[pos] == '-')) pos++
            while (pos < data.length && data[pos].isDigit()) pos++
        }
        return data.substring(start, pos).toDouble()
    }

    /** An arc flag, which is a single "0" or "1" that needn't be followed by a separator, e.g. "a10 10 0 100 20". */
    private fun flag(): Boolean {
        skipSeparators()
        require(pos < data.length && (data[pos] == '0' || data[pos] == '1')) { "Expected an arc flag at $pos: \"$data\"" }
        return data[pos++] == '1'
    }
}
