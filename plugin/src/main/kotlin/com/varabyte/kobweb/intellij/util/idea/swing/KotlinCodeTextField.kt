package com.varabyte.kobweb.intellij.util.idea.swing

import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import javax.swing.ScrollPaneConstants

class KotlinCodeTextField(code: String, project: Project) :
    EditorTextField(code, project, FileTypeManager.getInstance().getFileTypeByExtension("kt")) {

    private fun Editor.applyDiffHighlighting() {
        val markupModel = markupModel
        markupModel.removeAllHighlighters()

        val document = document
        for (i in 0 until document.lineCount) {
            val startOffset = document.getLineStartOffset(i)
            val endOffset = document.getLineEndOffset(i)
            val lineText = document.getText(TextRange(startOffset, endOffset))

            when {
                lineText.startsWith("+") -> {
                    markupModel.addLineHighlighter(DiffColors.DIFF_INSERTED, i, HighlighterLayer.ADDITIONAL_SYNTAX)
                }

                lineText.startsWith("-") -> {
                    markupModel.addLineHighlighter(DiffColors.DIFF_DELETED, i, HighlighterLayer.ADDITIONAL_SYNTAX)
                }
            }
        }
    }

    init {
        isViewer = true
        border = JBUI.Borders.customLine(JBColor.border(), 1)

        @Suppress("UsePropertyAccessSyntax") // Bad suggestion, causes a compile error
        setOneLineMode(false)
        setFontInheritedFromLAF(false) // Use editor font

        addSettingsProvider { editor ->
            editor.contentComponent.border = JBUI.Borders.empty(8)
            editor.scrollPane.apply {
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            }
            editor.applyDiffHighlighting()
            editor.document.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    editor.applyDiffHighlighting()
                }
            })
        }
    }
}