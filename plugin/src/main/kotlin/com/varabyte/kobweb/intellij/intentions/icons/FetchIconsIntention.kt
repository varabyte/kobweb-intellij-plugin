package com.varabyte.kobweb.intellij.intentions.icons

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.varabyte.kobweb.intellij.util.kobweb.icons.fetchIcons
import com.varabyte.kobweb.intellij.util.kobweb.icons.findIconSetId
import com.varabyte.kobweb.intellij.util.kobweb.icons.findIconsLockFile
import com.varabyte.kobweb.intellij.util.kobweb.icons.isIconsBlock
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Fetch the data for the icon sets declared in a build script's `kobweb { app|library { icons { ... } } }` block.
 *
 * This is available on the `icons` block and on each icon set inside it that was fetched before (e.g. so users can
 * fetch again after changing a set's version). Sets that were never fetched get the same action as a quick fix.
 */
class FetchIconsIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName() = UxGlobals.textBundle.message("intentions.fetch_icons.name")
    override fun getText() = familyName

    // Running a Gradle task doesn't change any code
    override fun startInWriteAction() = false
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile) = IntentionPreviewInfo.EMPTY

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (!element.containingFile.isWritable) return false
        val call = element.parentOfType<KtCallExpression>() ?: return false
        if (call.isIconsBlock()) return true
        val id = call.findIconSetId() ?: return false
        // Sets that were never fetched are already reported, with a quick fix that does the same thing
        return element.containingFile.findIconsLockFile(id) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        element.containingFile.fetchIcons()
    }
}
