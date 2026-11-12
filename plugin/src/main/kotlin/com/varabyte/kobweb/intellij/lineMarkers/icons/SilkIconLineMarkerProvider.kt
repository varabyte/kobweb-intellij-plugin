package com.varabyte.kobweb.intellij.lineMarkers.icons

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.ui.awt.RelativePoint
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import com.varabyte.kobweb.intellij.services.icons.IconCallPreviews
import com.varabyte.kobweb.intellij.util.kobweb.isInKobwebSource
import com.varabyte.kobweb.intellij.util.kobweb.isInReadableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconSet
import com.varabyte.kobweb.intellij.util.kobweb.silk.asSilkIconCall
import com.varabyte.kobweb.intellij.util.kobweb.silk.asSvgIconCall
import com.varabyte.kobweb.intellij.util.kobweb.silk.SvgIconCall
import com.varabyte.kobweb.intellij.util.kobweb.silk.readSvgIconShape
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Shows a preview of the icon in the gutter next to calls like `FaHouse()`, `MsHome()`, and next to calls to Silk's
 * SVG-defining `createIcon` method or Kobweb's `Svg`, as well as icons that delegate to them (e.g. `ArrowBackIcon()`).
 */
class SilkIconLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName() = UxGlobals.textBundle.message("line.markers.icon.preview.name")
    override fun getIcon() = UxGlobals.gutterIcon

    override fun getLineMarkerInfo(element: PsiElement) = createLineMarker(element, isSlowPass = false)

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            ProgressManager.checkCanceled()
            createLineMarker(element, isSlowPass = true)?.let { result.add(it) }
        }
    }

    /**
     * Creates a line marker for [element] if it's the name of a call to an icon.
     *
     * Resolving calls is the expensive part, so the fast pass handles names that are likely icons, without following
     * calls into other functions. The slow pass, in contrast, is willing to follow calls (see [readSvgIconShape]),
     * but it skips any icons the fast pass already marked, so no call gets two markers.
     */
    private fun createLineMarker(element: PsiElement, isSlowPass: Boolean): LineMarkerInfo<*>? {
        if (element !is LeafPsiElement) return null
        val name = element.text
        val mayBeFontIcon = SilkIconSet.entries.any { name.startsWith(it.functionPrefix) }
        // Kobweb's convention is to end SVG icon names in "Icon" (as with `createIcon` itself)
        val mayBeSvgIcon = name.endsWith("Icon")
        // Any other Composable might still be an icon, e.g. a user's `Logo()`. (Composable function names are
        // capitalized by convention, which rules out most other calls, e.g. `remember` or `listOf`.)
        val mayBeUnconventionalSvgIcon = !mayBeFontIcon && !mayBeSvgIcon && name.firstOrNull()?.isUpperCase() == true

        if (isSlowPass) {
            if (!mayBeSvgIcon && !mayBeUnconventionalSvgIcon) return null
            val call = element.findCall() ?: return null
            val isMarkedInFastPass = mayBeSvgIcon && createSvgIconLineMarker(element, call, followCalls = false) != null
            return if (isMarkedInFastPass) null else createSvgIconLineMarker(element, call, followCalls = true)
        }
        if (!mayBeFontIcon && !mayBeSvgIcon) return null
        val call = element.findCall() ?: return null
        if (mayBeFontIcon) createFontIconLineMarker(element, call)?.let { return it }
        return if (mayBeSvgIcon) createSvgIconLineMarker(element, call, followCalls = false) else null
    }

    /**
     * Returns the call this is the name of (e.g. `FaHouse()` for `FaHouse`), if it's in code where previews should be
     * shown.
     */
    private fun LeafPsiElement.findCall(): KtCallExpression? {
        if (elementType != KtTokens.IDENTIFIER) return null
        val nameRef = parent as? KtNameReferenceExpression ?: return null
        val call = nameRef.parent as? KtCallExpression ?: return null
        if (call.calleeExpression != nameRef) return null
        // Kobweb's own code counts too, so that icons defined there (e.g. in the Kobweb repo) get previews
        if (!isInReadableKobwebProject() && !isInKobwebSource()) return null
        return call
    }

    /** A line marker for an icon from one of Silk's icon font modules, e.g. `FaHouse()`. */
    private fun createFontIconLineMarker(element: LeafPsiElement, call: KtCallExpression): LineMarkerInfo<*>? {
        val iconCall = analyze(call) { call.asSilkIconCall() } ?: return null
        val previews = IconCallPreviews.find(iconCall) ?: return null
        val preview = previews.bestFit
        val description = buildString {
            append("${iconCall.iconSet.displayName}: ${preview.shape.name}")
            // Only mention the style if the user can choose it, e.g. not for icons that only come in one style
            iconCall.styleEnum?.displayNameFor(preview.style)?.let { append(" ($it)") }
        }

        return LineMarkerInfo(
            element,
            element.textRange,
            IconShapeIcon(preview.shape),
            /* tooltipProvider = */ { description },
            /* navHandler = */ { mouseEvent, _ ->
                SilkIconPopup(call, previews).show(RelativePoint(mouseEvent))
            },
            GutterIconRenderer.Alignment.LEFT,
            /* accessibleNameProvider = */ { description },
        )
    }

    /**
     * A line marker for a call to an SVG icon, either one of Silk's (e.g. `ArrowBackIcon()`) or one defined in the
     * user's own code, or for a call to `createIcon` or `Svg`, where SVG icons are defined.
     *
     * @param followCalls Whether to look for icons in the functions an icon function calls (see [readSvgIconShape]).
     */
    private fun createSvgIconLineMarker(
        element: LeafPsiElement,
        call: KtCallExpression,
        followCalls: Boolean,
    ): LineMarkerInfo<*>? {
        return when (val iconCall = call.asSvgIconCall()) {
            null -> null
            SvgIconCall.CreateIcon, SvgIconCall.Svg -> {
                val shape = call.cachedSvgIconShape() ?: return null
                createSvgIconLineMarker(element, shape, "Icon preview")
            }

            is SvgIconCall.NamedIcon -> {
                val shape = iconCall.function.cachedSvgIconShape(followCalls) ?: return null
                val sourceUrl = (iconCall as? SvgIconCall.SilkIcon)?.sourceUrl
                createSvgIconLineMarker(element, shape, "Icon: ${iconCall.name}", sourceUrl)
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
private fun KtNamedFunction.cachedSvgIconShape(followCalls: Boolean): IconShape? =
    if (followCalls) {
        cachedOrNull(SHAPE_KEY_DEEP_SEARCH) { readSvgIconShape(followCalls = true) }
    } else {
        cachedOrNull(SHAPE_KEY_SHALLOW_SEARCH) { readSvgIconShape(followCalls = false) }
    }

/** Like the [KtNamedFunction] version, but for a call to `createIcon` or `Svg`. */
private fun KtCallExpression.cachedSvgIconShape(): IconShape? =
    cachedOrNull(SHAPE_KEY_SHALLOW_SEARCH) { readSvgIconShape() }

private val SHAPE_KEY_SHALLOW_SEARCH = Key.create<CachedValue<IconShape?>>("kobweb.svgIconShape.shallowSearch")
private val SHAPE_KEY_DEEP_SEARCH = Key.create<CachedValue<IconShape?>>("kobweb.svgIconShape.deepSearch")

private fun <T : PsiElement> T.cachedOrNull(key: Key<CachedValue<IconShape?>>, read: T.() -> IconShape): IconShape? =
    CachedValuesManager.getCachedValue(this, key) {
        val shape = try {
            read()
        } catch (_: IllegalArgumentException) {
            null
        }
        CachedValueProvider.Result.create(shape, PsiModificationTracker.MODIFICATION_COUNT)
    }
