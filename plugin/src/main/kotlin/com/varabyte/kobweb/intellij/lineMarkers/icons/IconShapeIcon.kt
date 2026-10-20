package com.varabyte.kobweb.intellij.lineMarkers.icons

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.scale.JBUIScale
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon
import kotlin.math.max

/**
 * A Swing icon that paints an [IconShape] in the editor's foreground color, so it adapts to the current theme.
 *
 * @param baseSize The icon's width and height before scaling for the user's display. The default suits the gutter.
 */
class IconShapeIcon(private val shape: IconShape, private val baseSize: Int = 12) : Icon {
    private val size get() = JBUIScale.scale(baseSize)

    override fun getIconWidth() = size
    override fun getIconHeight() = size

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g2.color = EditorColorsManager.getInstance().globalScheme.defaultForeground

            // Fit the icon's box (shape.width x 1) into our square, centered
            val scale = size / max(shape.width, 1f).toDouble()
            g2.translate(x + (size - shape.width * scale) / 2, y + (size - scale) / 2)
            g2.scale(scale, scale)
            g2.fill(shape.path)
        } finally {
            g2.dispose()
        }
    }
}
