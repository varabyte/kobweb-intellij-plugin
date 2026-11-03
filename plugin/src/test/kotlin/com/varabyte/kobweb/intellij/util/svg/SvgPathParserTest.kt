package com.varabyte.kobweb.intellij.util.svg

import com.varabyte.truthish.assertThat
import com.varabyte.truthish.assertThrows
import org.junit.Test
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sqrt

class SvgPathParserTest {
    /** The path's segments as text, e.g. "M 1 2", with coordinates rounded to 3 decimal places. */
    private fun Path2D.segments(): List<String> {
        val coords = DoubleArray(6)
        val iterator = getPathIterator(null)
        return buildList {
            while (!iterator.isDone) {
                val type = iterator.currentSegment(coords)
                val (letter, pointCount) = when (type) {
                    PathIterator.SEG_MOVETO -> "M" to 1
                    PathIterator.SEG_LINETO -> "L" to 1
                    PathIterator.SEG_QUADTO -> "Q" to 2
                    PathIterator.SEG_CUBICTO -> "C" to 3
                    else -> "Z" to 0
                }
                add((listOf(letter) + coords.take(pointCount * 2).map { it.format() }).joinToString(" "))
                iterator.next()
            }
        }
    }

    private fun Double.format(): String {
        val rounded = (this * 1000).roundToLong() / 1000.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    /** The points along the path, with curves flattened into short lines. */
    private fun Path2D.flattenedPoints(): List<Pair<Double, Double>> {
        val coords = DoubleArray(6)
        val iterator = getPathIterator(null, 0.001)
        return buildList {
            while (!iterator.isDone) {
                if (iterator.currentSegment(coords) != PathIterator.SEG_CLOSE) add(coords[0] to coords[1])
                iterator.next()
            }
        }
    }

    @Test
    fun emptyPathData() {
        assertThat(SvgPathParser.parse("").segments()).isEmpty()
        assertThat(SvgPathParser.parse("  ").segments()).isEmpty()
    }

    @Test
    fun absoluteAndRelativeLines() {
        // ArrowBackIcon's path, as Kobweb's path DSL writes it
        val path = SvgPathParser.parse(
            "M 20 11 H 7.83 l 5.59 -5.59 L 12 4 l -8 8 l 8 8 l 1.41 -1.41 L 7.83 13 H 20 v -2 Z"
        )
        assertThat(path.segments()).containsExactly(
            "M 20 11",
            "L 7.83 11",
            "L 13.42 5.41",
            "L 12 4",
            "L 4 12",
            "L 12 20",
            "L 13.41 18.59",
            "L 7.83 13",
            "L 20 13",
            "L 20 11",
            "Z",
        ).inOrder()
    }

    @Test
    fun extraMovePairsAreLines() {
        assertThat(SvgPathParser.parse("M1 1 2 2").segments()).containsExactly("M 1 1", "L 2 2").inOrder()
        assertThat(SvgPathParser.parse("m1 1 2 2").segments()).containsExactly("M 1 1", "L 3 3").inOrder()
    }

    @Test
    fun repeatedArgumentsRepeatTheCommand() {
        assertThat(SvgPathParser.parse("M0 0 l1 0 0 1").segments())
            .containsExactly("M 0 0", "L 1 0", "L 1 1").inOrder()
    }

    @Test
    fun compactNumbers() {
        assertThat(SvgPathParser.parse("M.5.5l-.12-.63").segments())
            .containsExactly("M 0.5 0.5", "L 0.38 -0.13").inOrder()
        assertThat(SvgPathParser.parse("M1e1,2E-1").segments()).containsExactly("M 10 0.2")
    }

    @Test
    fun closingAPathReturnsToItsStart() {
        assertThat(SvgPathParser.parse("M1 1 L2 1 Z l0 1").segments())
            .containsExactly("M 1 1", "L 2 1", "Z", "L 1 2").inOrder()
    }

    @Test
    fun smoothCubicCurvesReflectThePreviousControlPoint() {
        assertThat(SvgPathParser.parse("M0 0 C1 0 2 1 2 2 S3 4 4 4").segments())
            .containsExactly("M 0 0", "C 1 0 2 1 2 2", "C 2 3 3 4 4 4").inOrder()
        // With no previous curve, the first control point is the current point
        assertThat(SvgPathParser.parse("M0 0 L1 1 s1 1 2 0").segments())
            .containsExactly("M 0 0", "L 1 1", "C 1 1 2 2 3 1").inOrder()
    }

    @Test
    fun smoothQuadraticCurvesReflectThePreviousControlPoint() {
        assertThat(SvgPathParser.parse("M0 0 Q1 1 2 0 T4 0 t2 0").segments())
            .containsExactly("M 0 0", "Q 1 1 2 0", "Q 3 -1 4 0", "Q 5 1 6 0").inOrder()
    }

    @Test
    fun fullCircleFromTwoArcs() {
        // CheckCircleIcon's circle, which also packs its arc flags together ("100" is flags 1, 0 and then 0)
        val path = SvgPathParser.parse("M12 2a10 10 0 100 20 10 10 0 000-20z")
        val points = path.flattenedPoints()
        assertThat(points.all { (x, y) -> hypot(x - 12, y - 12) in 9.99..10.01 }).isTrue()
        assertThat(points.minOf { it.first }).isWithin(0.01).of(2.0)
        assertThat(points.maxOf { it.first }).isWithin(0.01).of(22.0)
        assertThat(path.segments().last()).isEqualTo("Z")
    }

    @Test
    fun arcSweepFlagPicksTheDirection() {
        // In SVG's y-down coordinates, a sweep flag of 1 goes clockwise, here over the top of the circle
        assertThat(SvgPathParser.parse("M0 0 A1 1 0 0 1 2 0").flattenedPoints().minOf { it.second })
            .isWithin(0.01).of(-1.0)
        assertThat(SvgPathParser.parse("M0 0 A1 1 0 0 0 2 0").flattenedPoints().maxOf { it.second })
            .isWithin(0.01).of(1.0)
    }

    @Test
    fun arcLargeArcFlagPicksTheLongWayAround() {
        // A radius of 2 over a chord of 2 leaves the circle's center sqrt(3) away from the chord
        assertThat(SvgPathParser.parse("M0 0 A2 2 0 0 1 2 0").flattenedPoints().minOf { it.second })
            .isWithin(0.01).of(-(2 - sqrt(3.0)))
        assertThat(SvgPathParser.parse("M0 0 A2 2 0 1 1 2 0").flattenedPoints().minOf { it.second })
            .isWithin(0.01).of(-(2 + sqrt(3.0)))
    }

    @Test
    fun arcRadiiTooSmallForTheEndpointsAreScaledUp() {
        assertThat(SvgPathParser.parse("M0 0 A0.5 0.5 0 0 1 2 0").flattenedPoints().minOf { it.second })
            .isWithin(0.01).of(-1.0)
    }

    @Test
    fun rotatedArc() {
        // A 2x1 ellipse rotated a quarter turn is 1 wide and 2 tall
        val points = SvgPathParser.parse("M0 -2 A2 1 90 0 1 0 2 A2 1 90 0 1 0 -2").flattenedPoints()
        assertThat(points.minOf { it.first }).isWithin(0.01).of(-1.0)
        assertThat(points.maxOf { it.first }).isWithin(0.01).of(1.0)
        assertThat(points.minOf { it.second }).isWithin(0.01).of(-2.0)
        assertThat(points.maxOf { it.second }).isWithin(0.01).of(2.0)
    }

    @Test
    fun degenerateArcs() {
        // Zero radii draw a straight line, and an arc to the current point draws nothing
        assertThat(SvgPathParser.parse("M0 0 A0 1 0 0 1 2 0").segments()).containsExactly("M 0 0", "L 2 0").inOrder()
        assertThat(SvgPathParser.parse("M0 0 A1 1 0 0 1 0 0").segments()).containsExactly("M 0 0")
    }

    @Test
    fun invalidPathData() {
        assertThrows<IllegalArgumentException> { SvgPathParser.parse("L1 1") }
        assertThrows<IllegalArgumentException> { SvgPathParser.parse("M1") }
        assertThrows<IllegalArgumentException> { SvgPathParser.parse("M1 1 X2 2") }
        assertThrows<IllegalArgumentException> { SvgPathParser.parse("M1 1 Z 2 2") }
        assertThrows<IllegalArgumentException> { SvgPathParser.parse("M0 0 A1 1 0 2 0 1 1") }
        assertThrows<IllegalArgumentException> { SvgPathParser.parse("M. 1") }
    }
}
