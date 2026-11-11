package com.varabyte.kobweb.intellij.util.kobweb.silk

import com.varabyte.truthish.assertThat
import org.junit.Test

class SilkIconStyleTest {
    private fun SilkIconSet.browserUrl(iconName: String, styleId: String) =
        styles.single { it.id == styleId }.browserUrl(iconName)

    @Test
    fun fontAwesomeBrowserUrls() {
        assertThat(SilkIconSet.FONT_AWESOME.browserUrl("house", "fas"))
            .isEqualTo("https://fontawesome.com/icons/house?f=classic&s=solid")
        assertThat(SilkIconSet.FONT_AWESOME.browserUrl("house", "far"))
            .isEqualTo("https://fontawesome.com/icons/house?f=classic&s=regular")
        assertThat(SilkIconSet.FONT_AWESOME.browserUrl("github", "fab"))
            .isEqualTo("https://fontawesome.com/icons/github?f=brands&s=solid")
    }

    @Test
    fun materialIconsBrowserUrls() {
        assertThat(SilkIconSet.MATERIAL_ICONS.browserUrl("home", "mdi")).isEqualTo(
            "https://fonts.google.com/icons?icon.set=Material+Icons&icon.query=home" +
                "&selected=Material+Icons:home:&icon.style=Filled"
        )
        assertThat(SilkIconSet.MATERIAL_ICONS.browserUrl("home", "mdio")).isEqualTo(
            "https://fonts.google.com/icons?icon.set=Material+Icons&icon.query=home" +
                "&selected=Material+Icons+Outlined:home:&icon.style=Outlined"
        )
        assertThat(SilkIconSet.MATERIAL_ICONS.browserUrl("home", "mdir")).isEqualTo(
            "https://fonts.google.com/icons?icon.set=Material+Icons&icon.query=home" +
                "&selected=Material+Icons+Round:home:&icon.style=Rounded"
        )
        assertThat(SilkIconSet.MATERIAL_ICONS.browserUrl("home", "mdis")).isEqualTo(
            "https://fonts.google.com/icons?icon.set=Material+Icons&icon.query=home" +
                "&selected=Material+Icons+Sharp:home:&icon.style=Sharp"
        )
        assertThat(SilkIconSet.MATERIAL_ICONS.browserUrl("home", "mdit")).isEqualTo(
            "https://fonts.google.com/icons?icon.set=Material+Icons&icon.query=home" +
                "&selected=Material+Icons+Two+Tone:home:&icon.style=Two+tone"
        )
    }

    @Test
    fun materialSymbolsBrowserUrls() {
        assertThat(SilkIconSet.MATERIAL_SYMBOLS.browserUrl("home", "mso")).isEqualTo(
            "https://fonts.google.com/icons?icon.set=Material+Symbols&icon.query=home" +
                "&selected=Material+Symbols+Outlined:home:&icon.style=Outlined"
        )
        assertThat(SilkIconSet.MATERIAL_SYMBOLS.browserUrl("home", "msr")).isEqualTo(
            "https://fonts.google.com/icons?icon.set=Material+Symbols&icon.query=home" +
                "&selected=Material+Symbols+Rounded:home:&icon.style=Rounded"
        )
        assertThat(SilkIconSet.MATERIAL_SYMBOLS.browserUrl("home", "mss")).isEqualTo(
            "https://fonts.google.com/icons?icon.set=Material+Symbols&icon.query=home" +
                "&selected=Material+Symbols+Sharp:home:&icon.style=Sharp"
        )
    }

    @Test
    fun lucideBrowserUrl() {
        assertThat(SilkIconSet.LUCIDE.browserUrl("house", "lucide")).isEqualTo("https://lucide.dev/icons/house")
    }
}
