package com.varabyte.kobweb.intellij.wizards.module

import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.EmptyIcon
import com.varabyte.kobweb.intellij.lineMarkers.icons.IconShapeIcon
import com.varabyte.kobweb.intellij.services.icons.IconPreview
import com.varabyte.kobweb.intellij.services.icons.IconPreviewService
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconSet
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import javax.swing.ListCellRenderer

/** A choice in the new module wizard's icon set dropdown: either [None] or one of the icon sets in [IconSet]. */
internal sealed interface IconSetOption {
    val displayName: String

    data object None : IconSetOption {
        override val displayName: String
            get() = UxGlobals.textBundle.message("wizard.kobweb.module.icon.set.none")
    }

    /**
     * The icon sets that new modules can depend on, along with example icons that highlight the sets' styles.
     *
     * Material Icons (`mdi`) is left out on purpose: Google has replaced it with Material Symbols, which new code
     * should use instead.
     *
     * @property sampleIcons The upstream names of the icons previewed next to this option. Every set shows the same
     *   concepts (user, notifications, chat, idea, and send), so users can compare the sets' styles.
     */
    enum class IconSet(val iconSet: SilkIconSet, val sampleIcons: List<String>) : IconSetOption {
        FONT_AWESOME(SilkIconSet.FONT_AWESOME, listOf("user", "bell", "comment", "lightbulb", "paper-plane")),
        MATERIAL_SYMBOLS(
            SilkIconSet.MATERIAL_SYMBOLS,
            listOf("person", "notifications", "chat_bubble", "lightbulb", "send"),
        ),
        LUCIDE(SilkIconSet.LUCIDE, listOf("user", "bell", "message-circle", "lightbulb", "send"));

        override val displayName get() = iconSet.displayName
    }

    companion object {
        private const val SAMPLE_ICON_SIZE = 14

        /** Every option, in the order the dropdown lists them. */
        val entries: List<IconSetOption> = listOf(None) + IconSet.entries

        /**
         * Renders each option as its name, followed by its sample icons (if any) lined up at the right edge.
         *
         * The icons only appear in the dropdown's popup, not in the combo box itself. Until an icon set's previews
         * load, its icons are left blank too (see [IconPreviewService.ICON_SET_LOADED]).
         *
         * Blank icons still take up space, so the list doesn't change width once previews load. This also matters for
         * the combo box itself: Swing sizes it by rendering every option the way it renders the combo box, and the
         * popup is only as wide as the combo box.
         */
        // listCellRenderer is experimental, but we're willing to change code later if needed
        @Suppress("UnstableApiUsage")
        fun createRenderer(): ListCellRenderer<IconSetOption?> = listCellRenderer {
            // Swing passes null when nothing is selected
            val value = value ?: return@listCellRenderer
            text(value.displayName) { align = LcrInitParams.Align.LEFT }
            // All dropdown entries are rendered at index >= 0. For the label itself, we still need to render empty
            // icons or else dropdown entries will be too narrow.
            val isSelectedTextLabel = index == -1
            for (preview in (value as? IconSet)?.findSamplePreviews().orEmpty()) {
                val shown = preview.takeUnless { isSelectedTextLabel }
                icon(shown?.let { IconShapeIcon(it.shape, SAMPLE_ICON_SIZE) } ?: EmptyIcon.create(SAMPLE_ICON_SIZE))
            }
        }
    }
}

/**
 * Previews of this option's [sample icons][IconSetOption.IconSet.sampleIcons], with null for each one that has no
 * preview (e.g. until the icon set's previews have loaded, which this starts if needed).
 *
 * The icons are all shown in the first style they share, if any, so they look consistent. Since each set lists its
 * outline style first (e.g. Font Awesome's regular), that's the style shown.
 */
internal fun IconSetOption.IconSet.findSamplePreviews(): List<IconPreview?> {
    val namer = iconSet.wrapped.methodNamer
    // With no Kobweb version to go by, Font Awesome's previews are of FA 6, the version Kobweb wrapped longest
    val allPreviews = sampleIcons.map { name ->
        IconPreviewService.instance.findAllPreviews(iconSet, iconSetVersion = null, namer.nameFor(name))
    }
    val found = allPreviews.filter { it.isNotEmpty() }
    val sharedStyle = iconSet.styles.firstOrNull { style ->
        found.all { previews -> previews.any { it.style == style } }
    }
    return allPreviews.map { previews -> previews.find { it.style == sharedStyle } ?: previews.firstOrNull() }
}
