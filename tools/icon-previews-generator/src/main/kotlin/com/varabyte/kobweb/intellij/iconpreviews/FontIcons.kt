package com.varabyte.kobweb.intellij.iconpreviews

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.nio.file.Path

private const val EM = 1000f

object FontIcons {
    /**
     * Extract normalized outlines for the named glyphs in an icon font.
     *
     * Icon fonts design each glyph within the font's em square, so we use that as the icon's box: 1 em tall, and as
     * wide as the glyph's advance. Fonts disagree on where the em square sits relative to the baseline, so we find it
     * by centering on the vertical extent of all glyphs in the font.
     *
     * @param missing Names with no codepoint or no glyph in this font are added here instead of being returned.
     */
    fun extract(
        fontFile: Path,
        names: List<String>,
        codepoints: Map<String, Int>,
        missing: MutableList<String>,
    ): List<IconShape> {
        // TRUETYPE_FONT works for CFF-flavored .otf files too
        val font = Font.createFont(Font.TRUETYPE_FONT, fontFile.toFile()).deriveFont(EM)
        val frc = FontRenderContext(null, true, true)

        class RawGlyph(val name: String, val advance: Float, val outline: java.awt.Shape)

        val glyphs = names.mapNotNull { name ->
            val codepoint = codepoints[name]
            if (codepoint == null || !font.canDisplay(codepoint)) {
                missing.add("$name (${fontFile.fileName})")
                return@mapNotNull null
            }
            val glyphVector = font.createGlyphVector(frc, String(Character.toChars(codepoint)))
            RawGlyph(name, glyphVector.getGlyphMetrics(0).advance, glyphVector.outline)
        }
        if (glyphs.isEmpty()) return emptyList()

        val allBounds = glyphs.map { it.outline.bounds2D }.reduce(Rectangle2D::createUnion)
        val emTop = allBounds.centerY - EM / 2

        return glyphs.map { glyph ->
            val transform = AffineTransform.getScaleInstance(1.0 / EM, 1.0 / EM).apply { translate(0.0, -emTop) }
            IconShape(glyph.name, glyph.advance / EM, Path2D.Float(glyph.outline, transform))
        }
    }

    /** Parse a Google Fonts `.codepoints` file, whose lines look like "home e88a". */
    fun parseGoogleCodepoints(text: String): Map<String, Int> = text.lineSequence()
        .map { it.trim().split(' ') }
        .filter { it.size == 2 }
        .associate { (name, hex) -> name to hex.toInt(16) }

    /** Parse Font Awesome's all.css, whose icon rules look like `.fa-house::before { content: "\f015"; }`. */
    fun parseFontAwesomeCodepoints(css: String): Map<String, Int> =
        Regex("""\.fa-([a-z0-9-]+)::?before\s*\{\s*content:\s*"\\([0-9a-f]+)"""")
            .findAll(css)
            .associate { match -> match.groupValues[1] to match.groupValues[2].toInt(16) }
}
