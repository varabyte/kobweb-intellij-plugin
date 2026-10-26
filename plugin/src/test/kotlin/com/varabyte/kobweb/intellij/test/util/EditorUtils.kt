package com.varabyte.kobweb.intellij.test.util

import com.intellij.openapi.editor.Editor

/**
 * The selected text, with each line indented as it is in the editor, minus the indentation they all share (see
 * [trimIndent]), or null if nothing is selected.
 *
 * This lets tests compare multi-line selections against a `trimIndent`ed string, e.g. a selection that starts partway
 * into a line, like `from: "/a"` in `- from: "/a"`, followed by an indented `to: "/b"` on the next line, is
 * `"from: \"/a\"\nto: \"/b\""` rather than `"from: \"/a\"\n  to: \"/b\""`.
 */
val Editor.selectedTextWithoutIndent: String?
    get() {
        val text = selectionModel.selectedText ?: return null
        // The first line is missing the part of its line before the selection, so put that back as indentation
        val column = offsetToLogicalPosition(selectionModel.selectionStart).column
        return (" ".repeat(column) + text).trimIndent()
    }
