package com.varabyte.kobweb.intellij.iconpreviews

import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.math.ceil
import kotlin.math.max

private const val ICON_SIZE = 24
private const val CELL_SIZE = 32
private const val COLUMNS = 64

/**
 * Render every icon in each style into a grid, one PNG per style, for visually spot-checking the extracted data.
 *
 * @param fileName The name (without extension) of the PNG for each style.
 */
fun IconSet.writeContactSheets(outDir: Path, fileName: (IconStyle) -> String) {
    outDir.createDirectories()
    for (style in styles) {
        val rows = ceil(style.icons.size / COLUMNS.toDouble()).toInt()
        val image = BufferedImage(COLUMNS * CELL_SIZE, rows * CELL_SIZE, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color(0x2B2D30) // IntelliJ's dark editor background
        g.fillRect(0, 0, image.width, image.height)
        g.color = Color(0xDFE1E5)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        style.icons.forEachIndexed { i, icon ->
            // Fit the icon's box (width x 1) into the cell, centered
            val scale = ICON_SIZE / max(icon.width, 1f).toDouble()
            val transform = AffineTransform.getTranslateInstance(
                (i % COLUMNS) * CELL_SIZE + (CELL_SIZE - icon.width * scale) / 2,
                (i / COLUMNS) * CELL_SIZE + (CELL_SIZE - scale) / 2,
            ).apply { scale(scale, scale) }
            g.fill(transform.createTransformedShape(icon.path))
        }
        g.dispose()
        ImageIO.write(image, "png", outDir.resolve("${fileName(style)}.png").toFile())
    }
}
