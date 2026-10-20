package com.varabyte.kobweb.intellij.lineMarkers.icons

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.varabyte.kobweb.intellij.services.icons.IconPreviewService
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconSet
import com.varabyte.kobweb.intellij.util.kobweb.silk.asSilkIconCall
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Shows a preview of the icon in the gutter next to calls like `FaHouse()` or `MsHome()`.
 */
class SilkIconLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName() = UxGlobals.textBundle.message("line.markers.icon.preview.name")
    override fun getIcon() = UxGlobals.gutterIcon

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Target the identifier leaf of the call's name, e.g. `FaHouse` in `FaHouse()`
        if (element !is LeafPsiElement || element.elementType != KtTokens.IDENTIFIER) return null
        if (SilkIconSet.entries.none { element.text.startsWith(it.functionPrefix) }) return null
        val nameRef = element.parent as? KtNameReferenceExpression ?: return null
        val call = nameRef.parent as? KtCallExpression ?: return null
        if (call.calleeExpression != nameRef) return null
        if (!element.isInReadableKobwebProject()) return null

        val iconCall = analyze(call) { call.asSilkIconCall() } ?: return null
        val preview = IconPreviewService.instance.findPreview(iconCall) ?: return null

        return LineMarkerInfo(
            element,
            element.textRange,
            IconShapeIcon(preview.shape),
            /* tooltipProvider = */ { preview.description },
            /* navHandler = */ null,
            GutterIconRenderer.Alignment.LEFT,
            /* accessibleNameProvider = */ { preview.description },
        )
    }
}
