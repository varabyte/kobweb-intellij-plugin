package com.varabyte.kobweb.intellij.util.psi

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace

/**
 * Comments which, by basic heuristics, have been determined to be attached to some given element.
 *
 * ```
 * // Overhead comment
 * // which can span multiple lines.
 * val hello = "world" // Trailing comment
 * ```
 *
 * Overhead comments stand alone -- that is, they are comments which are bookended by newlines on each side.
 *
 * Trailing comments are those on the same line as some code, following it.
 */
class AttachedComments(
    /** Comments that live above the element, potentially spanning multiple lines. */
    val overhead: List<PsiComment>,
    /** Comments that live on the same line as the element, following it. */
    val trailing: PsiComment?,
) {
    fun hasAny() = overhead.isNotEmpty() || trailing != null

    private fun PsiComment.normalizeToString(): String {
        val content = when {
            text.startsWith("//") -> text.removePrefix("//").trim()
            text.startsWith("/*") -> text
                .removePrefix("/*")
                .removeSuffix("*/")
                .lines()
                .joinToString("\n") { it.trim().removePrefix("*").trim() }
                .trim()
            else -> error("Unexpected comment format: $text")
        }
        return content
    }

    private fun List<PsiComment>.normalizeToStrings(): List<String> {
        // The "split" case handles users using multi-line block comments
        return flatMap { it.normalizeToString().split('\n') }
    }

    fun normalizeToStrings(): AttachedCommentStrings {
        return AttachedCommentStrings(
            overhead.normalizeToStrings(),
            trailing?.normalizeToString()
        )
    }
}

/**
 * @see AttachedComments
 * @see AttachedComments.normalizeToStrings
 */
class AttachedCommentStrings(
    val overhead: List<String>,
    val trailing: String?,
)

fun PsiElement.findAttachedComments(): AttachedComments {
    val element = this

    val overhead = mutableListOf<PsiComment>()
    var trailing: PsiComment? = null

    run { // search for overhead comments
        var curr: PsiElement? = element
        // First, search for end of previous line before the line that contains this element
        while (curr != null) {
            if (curr is PsiWhiteSpace && curr.text.contains('\n')) {
                break
            }
            curr = curr.prevSibling ?: curr.parent
        }

        while (curr != null) {
            if (curr is PsiWhiteSpace) {
                // Keep going
            }
            else if (curr is PsiComment) {
                // This is only considered an overhead comment if it stands alone on its own line.
                val precedingSibling = curr.prevSibling
                if (precedingSibling is PsiWhiteSpace && !precedingSibling.text.contains('\n')) {
                    break
                }

                overhead.add(0, curr) // Prepend to keep top-to-bottom order
            } else {
                // Hit a code element, stop
                break
            }

            curr = curr.prevSibling
        }
    }

    run { // search for a trailing comment
        var curr: PsiElement? = element

        while (curr != null) {
            if (curr is PsiWhiteSpace && curr.text.contains('\n')) {
                break
            } else if (curr is PsiComment) {
                trailing = curr
                break
            }
            curr = curr.nextSibling ?: curr.parent
        }
    }

    return AttachedComments(overhead, trailing)
}