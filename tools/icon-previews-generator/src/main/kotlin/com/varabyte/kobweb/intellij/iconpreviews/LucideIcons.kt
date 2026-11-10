package com.varabyte.kobweb.intellij.iconpreviews

import com.varabyte.kobweb.icons.parsers.lucide.LucideIcons
import com.varabyte.kobweb.icons.parsers.lucide.SvgElement
import org.apache.batik.parser.AWTPathProducer
import java.awt.BasicStroke
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.io.StringReader

// Matches the defaults of Kobweb's generated Lucide composables: a 24x24 viewBox stroked at width 2 with round caps
private const val VIEW_BOX_SIZE = 24.0
private val STROKE = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

/**
 * Lucide's icons as preview data, with their deprecated aliases.
 *
 * Lucide icons are drawn with strokes, so we bake the stroke into an outline here, letting every icon set share the same
 * fill-only format. A few elements are additionally marked `fill="currentColor"`, in which case we fill their interiors
 * as well.
 */
fun LucideIcons.toPreviewIconSet(): PreviewIconSet {
    val shapes = icons.toSortedMap().map { (name, icon) ->
        val area = Area()
        icon.elements.forEach { element ->
            element.toShape(name)?.let { shape ->
                area.add(Area(STROKE.createStrokedShape(shape)))
                // Lucide's root <svg> sets fill="none", so only explicitly filled elements get their interior painted
                if (element.attributes["fill"].let { it != null && it != "none" }) area.add(Area(shape))
            }
        }
        val toUnitBox = AffineTransform.getScaleInstance(1 / VIEW_BOX_SIZE, 1 / VIEW_BOX_SIZE)
        IconShape(name, 1f, Path2D.Float(area, toUnitBox))
    }

    // Deprecated aliases are old names the icon was renamed from, which existing code may still call
    val aliases = icons.flatMap { (name, icon) ->
        icon.aliases.filter { it.isDeprecated }.map { alias -> alias.name to name }
    }.toMap()

    // Lucide has a single style; we use the generated composables' prefix as its ID
    return PreviewIconSet("lucide", listOf(PreviewIconStyle("lucide", shapes)), aliases)
}

/** This element as a Java2D shape, or null (with a warning) if it isn't a basic shape we support. */
private fun SvgElement.toShape(iconName: String): Shape? {
    fun attr(name: String) = attributes[name]
    fun num(name: String) = attr(name)?.toDouble() ?: 0.0
    return when (tag) {
        // SVG fills with the nonzero rule by default
        "path" -> AWTPathProducer.createShape(StringReader(attr("d").orEmpty()), Path2D.WIND_NON_ZERO)
        "circle" -> num("r").let { r -> Ellipse2D.Double(num("cx") - r, num("cy") - r, 2 * r, 2 * r) }
        "ellipse" -> {
            val (rx, ry) = num("rx") to num("ry")
            Ellipse2D.Double(num("cx") - rx, num("cy") - ry, 2 * rx, 2 * ry)
        }
        "line" -> Line2D.Double(num("x1"), num("y1"), num("x2"), num("y2"))
        "rect" -> {
            // Per the SVG spec, if only one of rx / ry is specified, the other matches it
            val rx = (attr("rx") ?: attr("ry"))?.toDouble() ?: 0.0
            val ry = (attr("ry") ?: attr("rx"))?.toDouble() ?: 0.0
            if (rx == 0.0 && ry == 0.0) {
                Rectangle2D.Double(num("x"), num("y"), num("width"), num("height"))
            } else {
                RoundRectangle2D.Double(num("x"), num("y"), num("width"), num("height"), 2 * rx, 2 * ry)
            }
        }
        "polyline", "polygon" -> {
            val coords = attr("points").orEmpty().trim().split(Regex("""[\s,]+""")).map { it.toDouble() }
            Path2D.Double().apply {
                coords.chunked(2).forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
                if (tag == "polygon") closePath()
            }
        }
        else -> {
            System.err.println("Warning: Skipping unsupported <$tag> element in Lucide icon \"$iconName\"")
            null
        }
    }
}
