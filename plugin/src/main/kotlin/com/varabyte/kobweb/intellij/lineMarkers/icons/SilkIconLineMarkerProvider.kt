package com.varabyte.kobweb.intellij.lineMarkers.icons

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.ui.awt.RelativePoint
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import com.varabyte.kobweb.intellij.services.icons.IconPreviewService
import com.varabyte.kobweb.intellij.services.icons.findRenderedBy
import com.varabyte.kobweb.intellij.util.kobweb.isInKobwebSource
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconSet
import com.varabyte.kobweb.intellij.util.kobweb.silk.asSilkIconCall
import com.varabyte.kobweb.intellij.util.kobweb.silk.asSilkSvgIconCall
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkSvgIconCall
import com.varabyte.kobweb.intellij.util.kobweb.silk.readSvgIconShape
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Shows a preview of the icon in the gutter next to calls like `FaHouse()`, `MsHome()`, or `ArrowBackIcon()`, and next
 * to calls to `createIcon` (where SVG icons, including the user's own, are defined).
 */
class SilkIconLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName() = UxGlobals.textBundle.message("line.markers.icon.preview.name")
    override fun getIcon() = UxGlobals.gutterIcon

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Target the identifier leaf of the call's name, e.g. `FaHouse` in `FaHouse()`
        if (element !is LeafPsiElement || element.elementType != KtTokens.IDENTIFIER) return null
        val mayBeFontIcon = SilkIconSet.entries.any { element.text.startsWith(it.functionPrefix) }
        // This includes `createIcon`
        val mayBeSvgIcon = element.text.endsWith("Icon")
        if (!mayBeFontIcon && !mayBeSvgIcon) return null
        val nameRef = element.parent as? KtNameReferenceExpression ?: return null
        val call = nameRef.parent as? KtCallExpression ?: return null
        if (call.calleeExpression != nameRef) return null
        // Kobweb's own code counts too, so that icons defined there (e.g. in the Kobweb repo) get previews
        if (!element.isInReadableKobwebProject() && !element.isInKobwebSource()) return null

        if (mayBeFontIcon) createFontIconLineMarker(element, call)?.let { return it }
        return if (mayBeSvgIcon) createSvgIconLineMarker(element, call) else null
    }

    /** A line marker for an icon from one of Silk's icon font modules, e.g. `FaHouse()`. */
    private fun createFontIconLineMarker(element: LeafPsiElement, call: KtCallExpression): LineMarkerInfo<*>? {
        val iconCall = analyze(call) { call.asSilkIconCall() } ?: return null
        val allPreviews = IconPreviewService.instance.findAllPreviews(iconCall)
        val preview = allPreviews.findRenderedBy(iconCall) ?: return null
        val description = buildString {
            append("${iconCall.iconSet.displayName}: ${preview.shape.name}")
            // Only mention the style if the user can choose it, e.g. not for icons that only come in one style
            if (iconCall.styleEnum != null) preview.style.displayName?.let { append(" ($it)") }
        }

        return LineMarkerInfo(
            element,
            element.textRange,
            IconShapeIcon(preview.shape),
            /* tooltipProvider = */ { description },
            /* navHandler = */ { mouseEvent, _ ->
                SilkIconPopup(call, iconCall, allPreviews).show(RelativePoint(mouseEvent))
            },
            GutterIconRenderer.Alignment.LEFT,
            /* accessibleNameProvider = */ { description },
        )
    }

    /**
     * A line marker for one of the SVG icons Silk defines in code (e.g. `ArrowBackIcon()`), or for a call to
     * `createIcon`, where SVG icons are defined.
     */
    private fun createSvgIconLineMarker(element: LeafPsiElement, call: KtCallExpression): LineMarkerInfo<*>? {
        return when (val iconCall = call.asSilkSvgIconCall()) {
            null -> null
            SilkSvgIconCall.CreateIcon -> {
                val shape = call.cachedSvgIconShape() ?: return null
                createSvgIconLineMarker(element, shape, "Icon preview")
            }

            is SilkSvgIconCall.Icon -> {
                val shape = iconCall.function.cachedSvgIconShape() ?: return null
                createSvgIconLineMarker(element, shape, "Silk: ${iconCall.name}", iconCall.sourceUrl)
            }
        }
    }

    private fun createSvgIconLineMarker(
        element: LeafPsiElement,
        shape: IconShape,
        description: String,
        sourceUrl: String? = null,
    ) = LineMarkerInfo(
        element,
        element.textRange,
        IconShapeIcon(shape),
        /* tooltipProvider = */ { description },
        /* navHandler = */ { mouseEvent, _ ->
            SvgIconPopup(description, shape, sourceUrl).show(RelativePoint(mouseEvent))
        },
        GutterIconRenderer.Alignment.LEFT,
        /* accessibleNameProvider = */ { description },
    )
}

/**
 * Reads the shape this icon function draws, or null if it can't be read (see [readSvgIconShape]).
 *
 * The result is cached, as the gutter asks for it on every highlighting pass.
 */
private fun KtNamedFunction.cachedSvgIconShape(): IconShape? = cachedOrNull { readSvgIconShape() }

/** Like the [KtNamedFunction] version, but for a call to `createIcon`. */
private fun KtCallExpression.cachedSvgIconShape(): IconShape? = cachedOrNull { readSvgIconShape() }

private fun <T : PsiElement> T.cachedOrNull(read: T.() -> IconShape): IconShape? =
    CachedValuesManager.getCachedValue(this) {
        val shape = try {
            read()
        } catch (_: IllegalArgumentException) {
            null
        }
        CachedValueProvider.Result.create(shape, PsiModificationTracker.MODIFICATION_COUNT)
    }
