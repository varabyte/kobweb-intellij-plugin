package com.varabyte.kobweb.intellij.lineMarkers.icons

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiFile
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import com.varabyte.kobweb.intellij.services.icons.IconPreview
import com.varabyte.kobweb.intellij.services.icons.IconCallPreviews
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconCall
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconStyle
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument

// Matches the size of standard IntelliJ icons, so previews line up with the radio buttons next to them
private const val PREVIEW_SIZE = 16

/**
 * A popup for a call to one of Kobweb's icon functions, showing its icon in every style it comes in.
 *
 * Choosing a style updates the call's `style` argument, where "Default" removes it. There's also a link to the icon's
 * page on its project's website.
 *
 * @param iconPreviews Previews of the icon that [call] renders.
 */
class SilkIconPopup(private val call: KtCallExpression, private val iconPreviews: IconCallPreviews) {
    private val iconCall get() = iconPreviews.call
    private val previews get() = iconPreviews.all

    // Captured up front, as reading PSI requires a read action, which the popup's Swing listeners don't run in
    private val file = ReadAction.compute<PsiFile, RuntimeException> { call.containingFile }
    private val project = ReadAction.compute<Project, RuntimeException> { call.project }

    fun show(point: RelativePoint) {
        val defaultPreview = previews.first()
        // Tracks the selected radio button, so "Open in browser" shows the style the user is currently looking at
        var currentPreview = iconPreviews.bestFit
        // If the style comes from an expression (e.g. a variable), we can't tell what it is, and shouldn't overwrite it
        val styleIsEditable = iconCall.styleArgument !is SilkIconCall.StyleArgument.Indirect

        val content = panel {
            row { label("${iconCall.iconSet.displayName}: ${defaultPreview.shape.name}").bold() }

            // Each preview the user can choose, paired with its style's name
            val styleEnum = iconCall.styleEnum
            val choosableStyles = previews.mapNotNull { preview ->
                styleEnum?.displayNameFor(preview.style)?.let { preview to it }
            }
            if (styleEnum == null || choosableStyles.size < 2) {
                row { icon(IconShapeIcon(currentPreview.shape, PREVIEW_SIZE)) }
            } else {
                buttonsGroup {
                    row {
                        previewIcon(defaultPreview)
                        val defaultLabel = styleEnum.displayNameFor(defaultPreview.style)
                            ?.let { message("popups.silk.icon.default.style", it) }
                            ?: message("popups.silk.icon.default")
                        radioButton(defaultLabel)
                            .enabled(styleIsEditable)
                            .applyToComponent {
                                isSelected = iconCall.styleArgument == SilkIconCall.StyleArgument.Absent
                                addActionListener {
                                    currentPreview = defaultPreview
                                    setStyle(null)
                                }
                            }
                    }
                    separator()
                    for ((preview, styleName) in choosableStyles) {
                        row {
                            previewIcon(preview)
                            radioButton(styleName.replaceFirstChar { it.uppercase() })
                                .enabled(styleIsEditable)
                                .applyToComponent {
                                    isSelected = (iconCall.styleArgument as? SilkIconCall.StyleArgument.Direct)
                                        ?.style == preview.style
                                    addActionListener {
                                        currentPreview = preview
                                        setStyle(preview.style)
                                    }
                                }
                        }
                    }
                }
                if (!styleIsEditable) {
                    row { comment(message("popups.silk.icon.style.is.expression")) }
                }
            }

            separator()
            row {
                link(message("popups.silk.icon.open.in.browser")) {
                    BrowserUtil.browse(currentPreview.style.browserUrl(currentPreview.shape.name))
                }
            }
        }
        content.showAsIconPopup(point)
    }

    /** Set the call's `style` argument to [style], or remove it if `null` (so the function's default is used). */
    @VisibleForTesting
    internal fun setStyle(style: SilkIconStyle?) {
        // All PSI access happens inside the write command, which also grants read access
        WriteCommandAction.writeCommandAction(project, file)
            .withName(message("popups.silk.icon.change.style.command"))
            .run<RuntimeException> {
                // The code may have changed since the popup opened
                if (!call.isValid) return@run

                val styleArgument = findStyleArgument()
                if (style == null) {
                    styleArgument?.let { removeArgument(it) }
                    return@run
                }
                // We only offer style choices for functions with a `style` parameter, so this is always set by now
                val styleEnum = iconCall.styleEnum ?: return@run
                val entry = styleEnum.entryFor(style) ?: return@run

                // Write the style's fully qualified name, then let the IDE shorten it (adding an import if needed)
                val factory = KtPsiFactory(project)
                val styleExpression = factory.createExpression("${styleEnum.fqName}.$entry")
                val added = if (styleArgument?.getArgumentExpression() != null) {
                    styleArgument.getArgumentExpression()!!.replace(styleExpression)
                } else {
                    call.valueArgumentList?.addArgument(
                        factory.createArgument(styleExpression, Name.identifier("style"))
                    )
                }
                (added as? KtElement)?.let { ShortenReferencesFacility.getInstance().shorten(it) }
            }
    }

    /**
     * Find the call's current `style` argument.
     *
     * The one we resolved when creating the popup may be gone by now (e.g. if the user switched to "Default"), in which
     * case any argument we've added since then is named.
     */
    private fun findStyleArgument(): KtValueArgument? {
        return (iconCall.styleArgument as? SilkIconCall.StyleArgument.Present)?.argument?.takeIf { it.isValid }
            ?: call.valueArguments.find { it.getArgumentName()?.asName?.asString() == "style" }
    }

    /**
     * Remove [argument] from the call, naming any positional arguments after it first.
     *
     * Otherwise, they'd shift over into the wrong parameters, e.g. removing the style from
     * `FaSquare(Modifier, IconStyle.FILLED, IconSize.LG)` results in `FaSquare(Modifier, size = IconSize.LG)` rather
     * than `FaSquare(Modifier, IconSize.LG)` (which passes the size in as the style).
     */
    private fun removeArgument(argument: KtValueArgument) {
        val argumentList = call.valueArgumentList ?: return
        val factory = KtPsiFactory(project)
        argumentList.arguments
            .dropWhile { it != argument }
            .drop(1)
            .filter { !it.isNamed() }
            .forEach { following ->
                // Missing if the code has changed since the popup opened, in which case we leave the argument alone
                val name = iconCall.parameterNames[following] ?: return@forEach
                val expression = following.getArgumentExpression() ?: return@forEach
                following.replace(factory.createArgument(expression, name))
            }
        argumentList.removeArgument(argument)
    }

    /** A preview of a style, as shown next to the radio button that chooses it. */
    private fun Row.previewIcon(preview: IconPreview) =
        icon(IconShapeIcon(preview.shape, PREVIEW_SIZE))
            .gap(RightGap.SMALL)
            // A little extra breathing room between the preview and the radio button
            .customize(UnscaledGaps(right = 12))

}

/**
 * A popup for an SVG icon, e.g. for a call to `ArrowBackIcon()` or to `createIcon` itself, showing the icon.
 *
 * @param sourceUrl A link to the icon's source, if it's somewhere other than the code the user is looking at.
 */
class SvgIconPopup(private val title: String, private val shape: IconShape, private val sourceUrl: String? = null) {
    fun show(point: RelativePoint) {
        panel {
            row { label(title).bold() }
            row { icon(IconShapeIcon(shape, PREVIEW_SIZE)) }
            if (sourceUrl != null) {
                separator()
                row { link(message("popups.silk.icon.view.source")) { BrowserUtil.browse(sourceUrl) } }
            }
        }.showAsIconPopup(point)
    }
}

private fun DialogPanel.showAsIconPopup(point: RelativePoint) {
    // Keep content (especially the previews) from touching the edges of the popup
    border = JBUI.Borders.empty(8, 12)

    JBPopupFactory.getInstance()
        .createComponentPopupBuilder(this, null)
        .setRequestFocus(true)
        .setCancelOnClickOutside(true)
        .createPopup()
        .show(point)
}

private fun message(key: String, vararg params: Any) = UxGlobals.textBundle.message(key, *params)
