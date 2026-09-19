package com.varabyte.kobweb.intellij.util.text

enum class TruncateAt {
    START,
    END,
    MIDDLE
}

fun String.truncate(length: Int, truncateAt: TruncateAt = TruncateAt.END, ellipsis: String = "…"): String {
    require(length > 0) { "Truncate length must be greater than zero." }
    if (this.length <= length) return this

    val visibleCharCount = (length - ellipsis.length).coerceAtLeast(1)

    when (truncateAt) {
        TruncateAt.START -> return ellipsis + this.drop(this.length - visibleCharCount)
        TruncateAt.END -> return this.dropLast(this.length - visibleCharCount) + ellipsis
        TruncateAt.MIDDLE -> {
            // Bias to the left side, e.g. prefer "01…9" over "0…89"
            val firstHalfVisibleCharCount = (visibleCharCount + 1) / 2
            val lastHalfVisibleCharCount = visibleCharCount - firstHalfVisibleCharCount

            return this.substring(0, firstHalfVisibleCharCount) + ellipsis + this.substring(this.length - lastHalfVisibleCharCount)
        }
    }
}