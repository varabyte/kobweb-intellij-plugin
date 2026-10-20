package com.varabyte.kobweb.intellij.iconpreviews

import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.roundToInt

/**
 * A single icon outline.
 *
 * Coordinates are normalized so the icon's design box is 1 unit tall, with (0, 0) at its top-left and y pointing down.
 * Most icons are square (width 1), but some (particularly Font Awesome ones) are wider or narrower than they are tall.
 */
class IconShape(val name: String, val width: Float, val path: Path2D.Float)

/** A visual variant of an icon set. Its ID is the prefix Kobweb uses for it in its icon list files, e.g. "fas". */
class IconStyle(val id: String, val icons: List<IconShape>)

/**
 * A collection of icons, like Font Awesome or Material Symbols, in one or more styles.
 *
 * @property id The icon set's ID, e.g. "fa" or "ms". Not to be confused with its styles' IDs (e.g. "fas" or "mso").
 *   This is also the name of the set's data file (e.g. "fa.bin") and its key in the manifest.
 * @property aliases Deprecated icon names mapped to their replacement names.
 */
class IconSet(val id: String, val styles: List<IconStyle>, val aliases: Map<String, String> = emptyMap())

/**
 * Compact binary encoding for an [IconSet].
 *
 * Outlines are quantized to a grid of [GRID_UNITS] units per icon height. That's still overkill for gutter icons but
 * leaves headroom for larger previews (e.g. in documentation popups). Coordinates are written as zigzag varint deltas,
 * and the whole stream is gzipped.
 *
 * ```
 * "KIP" formatVersion:u8
 * styleCount:varint
 *   styleId:utf iconCount:varint
 *     name:utf width:varint windingRule:u8 (segmentType:u8 coords:varint*)* END:u8
 * aliasCount:varint
 *   from:utf to:utf
 * ```
 */
object IconPreviewFormat {
    const val VERSION = 1
    const val GRID_UNITS = 64
    private val MAGIC = "KIP".toByteArray()
    private const val END_OF_PATH = 0xFF

    fun write(iconSet: IconSet, output: OutputStream) {
        DataOutputStream(GZIPOutputStream(output)).use { out ->
            out.write(MAGIC)
            out.writeByte(VERSION)
            out.writeVarInt(iconSet.styles.size)
            for (style in iconSet.styles) {
                out.writeUTF(style.id)
                out.writeVarInt(style.icons.size)
                for (icon in style.icons) {
                    out.writeUTF(icon.name)
                    out.writeVarInt(icon.width.quantize())
                    out.writePath(icon.path)
                }
            }
            out.writeVarInt(iconSet.aliases.size)
            for ((from, to) in iconSet.aliases) {
                out.writeUTF(from)
                out.writeUTF(to)
            }
        }
    }

    /**
     * Read an icon set previously written by [write]. The stream is closed afterward.
     *
     * Only data written in exactly this [VERSION] of the format can be read. Since the data is downloaded separately
     * from the code that reads it, callers should be prepared for this to fail (e.g. an older plugin reading data
     * published after a format change) and treat any exception as "this data can't be used".
     *
     * @param id The ID to give the returned [IconSet] (it isn't stored in the data itself).
     * @throws IllegalArgumentException if the data isn't an icon preview file, or is in an unsupported format version.
     * @throws java.io.IOException if the data can't be read, e.g. if it isn't gzipped or is truncated.
     * @throws IllegalStateException if the data is otherwise malformed.
     */
    fun read(id: String, input: InputStream): IconSet {
        DataInputStream(GZIPInputStream(input)).use { inp ->
            val magic = ByteArray(MAGIC.size).also { inp.readFully(it) }
            require(magic.contentEquals(MAGIC)) { "Not an icon preview file" }
            val version = inp.readUnsignedByte()
            require(version == VERSION) { "Unsupported icon preview format version $version" }
            val styles = List(inp.readVarInt()) {
                val styleId = inp.readUTF()
                val icons = List(inp.readVarInt()) {
                    val name = inp.readUTF()
                    val width = inp.readVarInt().toFloat() / GRID_UNITS
                    IconShape(name, width, inp.readPath())
                }
                IconStyle(styleId, icons)
            }
            val aliases = buildMap {
                repeat(inp.readVarInt()) { put(inp.readUTF(), inp.readUTF()) }
            }
            return IconSet(id, styles, aliases)
        }
    }

    private fun Float.quantize() = (this * GRID_UNITS).roundToInt()

    private fun DataOutputStream.writePath(path: Path2D) {
        val coords = FloatArray(6)
        val iterator = path.getPathIterator(null)
        writeByte(iterator.windingRule)
        var lastX = 0
        var lastY = 0
        while (!iterator.isDone) {
            val type = iterator.currentSegment(coords)
            writeByte(type)
            repeat(type.pointCount()) { i ->
                val x = coords[2 * i].quantize()
                val y = coords[2 * i + 1].quantize()
                writeVarInt(zigzag(x - lastX))
                writeVarInt(zigzag(y - lastY))
                lastX = x
                lastY = y
            }
            iterator.next()
        }
        writeByte(END_OF_PATH)
    }

    private fun DataInputStream.readPath(): Path2D.Float {
        val path = Path2D.Float(readUnsignedByte())
        val coords = FloatArray(6)
        var lastX = 0
        var lastY = 0
        while (true) {
            val type = readUnsignedByte()
            if (type == END_OF_PATH) break
            repeat(type.pointCount()) { i ->
                lastX += unzigzag(readVarInt())
                lastY += unzigzag(readVarInt())
                coords[2 * i] = lastX.toFloat() / GRID_UNITS
                coords[2 * i + 1] = lastY.toFloat() / GRID_UNITS
            }
            when (type) {
                PathIterator.SEG_MOVETO -> path.moveTo(coords[0], coords[1])
                PathIterator.SEG_LINETO -> path.lineTo(coords[0], coords[1])
                PathIterator.SEG_QUADTO -> path.quadTo(coords[0], coords[1], coords[2], coords[3])
                PathIterator.SEG_CUBICTO -> path.curveTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5])
                PathIterator.SEG_CLOSE -> path.closePath()
                else -> error("Unexpected path segment type $type")
            }
        }
        return path
    }

    private fun Int.pointCount() = when (this) {
        PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO -> 1
        PathIterator.SEG_QUADTO -> 2
        PathIterator.SEG_CUBICTO -> 3
        else -> 0
    }

    private fun zigzag(value: Int) = (value shl 1) xor (value shr 31)
    @Suppress("SpellCheckingInspection")
    private fun unzigzag(value: Int) = (value ushr 1) xor -(value and 1)

    private fun DataOutputStream.writeVarInt(value: Int) {
        var remaining = value
        while (remaining and 0x7F.inv() != 0) {
            writeByte((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
        writeByte(remaining)
    }

    private fun DataInputStream.readVarInt(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = readUnsignedByte()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }
}
