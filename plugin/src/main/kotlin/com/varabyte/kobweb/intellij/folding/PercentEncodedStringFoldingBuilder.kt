package com.varabyte.kobweb.intellij.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.settings.KobwebAppSettingsService
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.text.isDefaultIgnorableCodePoint
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.text.BreakIterator

/**
 * A character that Unicode considers visible, i.e. anything other than whitespace, control characters, surrogates, and
 * unassigned code points (see "graph" in https://www.unicode.org/reports/tr18/#Compatibility_Properties).
 *
 * The `(?U)` flag makes `\p{Graph}` follow Unicode's definition instead of matching only visible ASCII characters.
 */
private val GRAPHIC_CHARACTER = Regex("(?U)\\p{Graph}")

/**
 * Runs of percent-encoded bytes that are part of non-ASCII characters, e.g. "%C3%A9" in "caf%C3%A9%20au%20lait".
 *
 * In UTF-8, every byte of a non-ASCII character is 0x80 or higher, while every ASCII character is a single byte below
 * that, so this finds all of a run's characters without needing to know how many bytes each one is.
 */
private val ENCODED_NON_ASCII_BYTES = Regex("(%[89A-Fa-f][0-9A-Fa-f])+")

/**
 * Folds percent-encoded characters in strings into the characters they stand for, e.g. showing
 * `Link("/blog/caf%C3%A9")` as `Link("/blog/café")`.
 *
 * Only non-ASCII characters are folded, as those are the ones that are unreadable when encoded (and that Kobweb
 * requires to be encoded in routes). Encoded ASCII characters, e.g. "%20" or "%2F", are left as they are, since they're
 * short and showing them decoded would be misleading (e.g. "%2F" isn't a path separator, but "/" would look like one).
 */
class PercentEncodedStringFoldingBuilder : FoldingBuilderEx() {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (!root.isInReadableKobwebProject()) return FoldingDescriptor.EMPTY_ARRAY

        return PsiTreeUtil.findChildrenOfType(root, KtLiteralStringTemplateEntry::class.java)
            .filter { entry -> '%' in entry.text }
            .flatMap { entry ->
                entry.text.findEncodedCharacterRuns().map { (range, decoded) ->
                    FoldingDescriptor(entry.node, range.shiftRight(entry.textRange.startOffset), null, decoded)
                }
            }
            .toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String? = null

    override fun isCollapsedByDefault(node: ASTNode) =
        KobwebAppSettingsService.getInstance().state.folding.collapsePercentEncodedStrings

    /**
     * Returns each run of percent-encoded characters in this text that should be shown decoded, along with its decoded
     * value, e.g. the range of "%C3%A9" with "é" for "caf%C3%A9".
     */
    private fun String.findEncodedCharacterRuns(): List<Pair<TextRange, String>> {
        return ENCODED_NON_ASCII_BYTES.findAll(this).flatMap { match ->
            // e.g. "%C3%A9" becomes the bytes 0xC3 and 0xA9
            val bytes = match.value.replace("%", "").hexToByteArray()
            val decoded = try {
                // Unlike `String(bytes)`, this rejects invalid UTF-8 instead of replacing it
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                return@flatMap emptyList()
            }
            decoded.findVisibleRuns(encodedStart = match.range.first)
        }.toList()
    }

    /**
     * Splits this decoded text around any invisible characters (e.g. a non-breaking space), since showing those decoded
     * would hide them, and returns each remaining run along with its range in the encoded text.
     *
     * Text is split into characters as users see them, so that sequences such as emoji joined by zero-width joiners
     * (e.g. "👩‍💻") stay together, while invisible characters on their own are left out.
     *
     * @param encodedStart Where this text's encoding starts, e.g. 3 for the "%C3%A9" in "caf%C3%A9".
     */
    private fun String.findVisibleRuns(encodedStart: Int): List<Pair<TextRange, String>> {
        val boundaries = BreakIterator.getCharacterInstance().apply { setText(this@findVisibleRuns) }
        val characters = generateSequence(boundaries.first()) { boundaries.next().takeIf { it != BreakIterator.DONE } }
            .zipWithNext { start, end -> substring(start, end) }

        val runs = mutableListOf<Pair<TextRange, String>>()
        val run = StringBuilder()
        var runStart = encodedStart
        var encodedOffset = encodedStart
        for (character in characters) {
            // Each byte of the character's UTF-8 encoding takes three characters, e.g. "%C3"
            val encodedLength = character.toByteArray(Charsets.UTF_8).size * 3
            if (character.codePoints().allMatch { it.isInvisible() }) {
                if (run.isNotEmpty()) runs.add(TextRange(runStart, encodedOffset) to run.toString())
                run.clear()
                runStart = encodedOffset + encodedLength
            } else {
                run.append(character)
            }
            encodedOffset += encodedLength
        }
        if (run.isNotEmpty()) runs.add(TextRange(runStart, encodedOffset) to run.toString())
        return runs
    }

    /**
     * Returns true if this code point is a character that can't be seen, e.g. a zero-width space.
     */
    private fun Int.isInvisible(): Boolean =
        // Unicode considers some characters "graphic" even though they're normally rendered as nothing, e.g. zero-width
        // spaces, so also check for those
        isDefaultIgnorableCodePoint() || !GRAPHIC_CHARACTER.matches(Character.toString(this))
}
