package com.varabyte.kobweb.intellij.util.kobweb.silk

import com.intellij.util.text.SemVer
import com.varabyte.truthish.assertThat
import org.junit.Test

class SilkIconSetTest {
    @Test
    fun fontAwesomePreviewIdsDependOnMajorVersion() {
        assertThat(SilkIconSet.FONT_AWESOME.previewId(SemVer.parseFromText("6.5.2"))).isEqualTo("fa6")
        assertThat(SilkIconSet.FONT_AWESOME.previewId(SemVer.parseFromText("7.1.0"))).isEqualTo("fa7")
        // Kobweb wrapped Font Awesome 6 before it recorded the version
        assertThat(SilkIconSet.FONT_AWESOME.previewId(null)).isEqualTo("fa6")
    }

    @Test
    fun otherPreviewIdsIgnoreVersion() {
        assertThat(SilkIconSet.MATERIAL_ICONS.previewId(SemVer.parseFromText("1.0.0"))).isEqualTo("mdi")
        assertThat(SilkIconSet.MATERIAL_SYMBOLS.previewId(null)).isEqualTo("ms")
        assertThat(SilkIconSet.LUCIDE.previewId(SemVer.parseFromText("1.70.0"))).isEqualTo("lucide")
    }
}
