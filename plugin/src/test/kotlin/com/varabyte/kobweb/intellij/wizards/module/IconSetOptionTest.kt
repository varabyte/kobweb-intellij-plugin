@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.wizards.module

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.varabyte.kobweb.intellij.iconpreviews.IconShape
import com.varabyte.kobweb.intellij.iconpreviews.PreviewIconSet
import com.varabyte.kobweb.intellij.iconpreviews.PreviewIconStyle
import com.varabyte.kobweb.intellij.services.icons.IconPreviewService
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconSet
import com.varabyte.truthish.assertThat
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

class IconSetOptionTest : BasePlatformTestCase() {
    // Stand-in outlines, so tests don't need to download real icon data. Only the names and styles matter here.
    private fun icons(vararg names: String) =
        names.map { IconShape(it, 1f, Path2D.Float(Rectangle2D.Float(0f, 0f, 1f, 1f))) }

    fun testSamplesUseTheFirstStyleTheyAllShare() {
        IconPreviewService.instance.useIconSetForTests(
            SilkIconSet.FONT_AWESOME,
            PreviewIconSet(
                "fa6",
                listOf(
                    PreviewIconStyle("fas", icons("user", "bell", "comment", "lightbulb", "paper-plane")),
                    PreviewIconStyle("far", icons("user", "bell", "comment", "lightbulb", "paper-plane")),
                ),
            ),
            testRootDisposable,
        )

        val previews = IconSetOption.IconSet.FONT_AWESOME.findSamplePreviews()

        // Font Awesome prefers regular over solid, whatever order the data lists them in
        assertThat(previews.map { it?.shape?.name })
            .containsExactly("user", "bell", "comment", "lightbulb", "paper-plane")
            .inOrder()
        assertThat(previews.map { it?.style?.id }.distinct()).containsExactly("far")
    }

    fun testSamplesFallBackToAStyleTheyAllShare() {
        IconPreviewService.instance.useIconSetForTests(
            SilkIconSet.FONT_AWESOME,
            PreviewIconSet(
                "fa6",
                listOf(
                    PreviewIconStyle("far", icons("user", "bell", "lightbulb", "paper-plane")),
                    PreviewIconStyle("fas", icons("user", "bell", "comment", "lightbulb", "paper-plane")),
                ),
            ),
            testRootDisposable,
        )

        val previews = IconSetOption.IconSet.FONT_AWESOME.findSamplePreviews()

        assertThat(previews.map { it?.shape?.name })
            .containsExactly("user", "bell", "comment", "lightbulb", "paper-plane")
            .inOrder()
        assertThat(previews.map { it?.style?.id }.distinct()).containsExactly("fas")
    }

    fun testMissingSampleHasNoPreview() {
        IconPreviewService.instance.useIconSetForTests(
            SilkIconSet.FONT_AWESOME,
            PreviewIconSet(
                "fa6",
                listOf(
                    PreviewIconStyle("far", icons("user", "bell", "lightbulb", "paper-plane")),
                    PreviewIconStyle("fas", icons("user", "bell", "lightbulb", "paper-plane")),
                ),
            ),
            testRootDisposable,
        )

        val previews = IconSetOption.IconSet.FONT_AWESOME.findSamplePreviews()

        // The others still share the first style, now that the missing icon doesn't rule it out
        assertThat(previews.map { it?.shape?.name }).containsExactly("user", "bell", null, "lightbulb", "paper-plane")
            .inOrder()
        assertThat(previews.filterNotNull().map { it.style.id }.distinct()).containsExactly("far")
    }
}
