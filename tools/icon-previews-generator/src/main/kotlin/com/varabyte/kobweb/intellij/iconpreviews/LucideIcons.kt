package com.varabyte.kobweb.intellij.iconpreviews

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class LucideIcons(val version: String, val iconSet: IconSet) {
    companion object {
        /**
         * Convert Kobweb's `lucide-icons.json` into filled outlines.
         *
         * Lucide icons are drawn with strokes, so we bake the stroke into an outline here, letting every icon set share
         * the same fill-only format. A few elements are additionally marked `fill="currentColor"`, in which case we
         * fill their interiors as well.
         */
        fun parse(json: String): LucideIcons {
            val root = Json.parseToJsonElement(json).jsonObject
            val icons = root.getValue("icons").jsonObject.map { (name, elements) ->
                val area = Area()
                elements.jsonArray.forEach { element ->
                    val obj = element.jsonObject
                    val tag = obj.getValue("tag").jsonPrimitive.content
                    val attrs = obj["attrs"]?.jsonObject ?: JsonObject(emptyMap())
                    val attr = { key: String -> attrs[key]?.jsonPrimitive?.content }
                    svgElementToShape(name, tag, attr)?.let { shape ->
                        area.add(Area(STROKE.createStrokedShape(shape)))
                        // Lucide's root <svg> sets fill="none", so only explicitly filled elements get their interior
                        // painted
                        if (attr("fill").let { it != null && it != "none" }) area.add(Area(shape))
                    }
                }
                val toUnitBox = AffineTransform.getScaleInstance(1 / VIEW_BOX_SIZE, 1 / VIEW_BOX_SIZE)
                IconShape(name, 1f, Path2D.Float(area, toUnitBox))
            }
            val aliases = root["deprecated"]?.jsonObject.orEmpty().mapValues { it.value.jsonPrimitive.content }
            return LucideIcons(
                root.getValue("version").jsonPrimitive.content,
                // Lucide has a single style; we use the generated composables' prefix as its ID
                IconSet("lucide", listOf(IconStyle("lucide", icons)), aliases),
            )
        }

        private fun svgElementToShape(iconName: String, tag: String, attr: (String) -> String?): Shape? {
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
    }
}
