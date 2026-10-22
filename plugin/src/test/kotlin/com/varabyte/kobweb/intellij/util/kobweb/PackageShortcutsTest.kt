package com.varabyte.kobweb.intellij.util.kobweb

import com.varabyte.truthish.assertThat
import org.jetbrains.kotlin.name.FqName
import org.junit.Test

class PackageShortcutsTest {
    private fun resolve(base: String?, name: String) = PackageShortcuts(base?.let(::FqName)).resolve(name)?.asString()
    private fun relativize(base: String?, fqn: String) = PackageShortcuts(base?.let(::FqName)).relativize(FqName(fqn))

    @Test
    fun resolveExpandsRelativeNames() {
        assertThat(resolve("com.example", ".components.PageLayout")).isEqualTo("com.example.components.PageLayout")
        assertThat(resolve("com.example", ".")).isEqualTo("com.example")
        assertThat(resolve("com.example", "org.other.PageLayout")).isEqualTo("org.other.PageLayout")
        assertThat(resolve("com.example", "PageLayout")).isEqualTo("PageLayout")

        // Root base package
        assertThat(resolve("", ".components.PageLayout")).isEqualTo("components.PageLayout")
        assertThat(resolve("", ".")).isEqualTo("")
    }

    @Test
    fun resolveRequiresBaseOnlyForRelativeNames() {
        assertThat(resolve(null, ".components.PageLayout")).isNull()
        assertThat(resolve(null, "org.other.PageLayout")).isEqualTo("org.other.PageLayout")
    }

    @Test
    fun relativizeOnlyRelativizesNamesUnderBase() {
        assertThat(relativize("com.example", "com.example.components.PageLayout")).isEqualTo(".components.PageLayout")
        assertThat(relativize("com.example", "org.other.PageLayout")).isEqualTo("org.other.PageLayout")
        // Shares a prefix with the base but isn't inside it
        assertThat(relativize("com.example", "com.examples.PageLayout")).isEqualTo("com.examples.PageLayout")
        assertThat(relativize(null, "com.example.PageLayout")).isEqualTo("com.example.PageLayout")
        assertThat(relativize("", "com.example.PageLayout")).isEqualTo("com.example.PageLayout")
    }
}
