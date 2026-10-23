package com.varabyte.kobweb.intellij.completion.route

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Shows the completion popup when a slash is typed in a route, e.g. `Link("/")` or `Link("/blog/")`, as that's where
 * people start typing a route or move on to its next segment.
 *
 * (Typing letters already shows the popup, see [RouteCompletionConfidence].)
 */
class RouteTypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (charTyped != '/' || file !is KtFile) return Result.CONTINUE

        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val offset = editor.caretModel.offset
        val stringExpr = file.findElementAt(offset)?.parentOfType<KtStringTemplateExpression>(withSelf = true)
            ?: return Result.CONTINUE
        // The slash hasn't been typed yet, so this is the text before it
        if (stringExpr.findRoutePrefix(offset) != null) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }
        return Result.CONTINUE
    }
}
