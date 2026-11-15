@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.util.github

import com.varabyte.truthish.assertThat
import org.junit.Test

class GitHubRepoTest {
    @Test
    fun `file urls include the repo, ref, and path`() {
        val repo = GitHubRepo("varabyte/kobweb")

        assertThat(repo.fileUrl("gradle/libs.versions.toml"))
            .isEqualTo("https://github.com/varabyte/kobweb/blob/main/gradle/libs.versions.toml")
        assertThat(repo.rawFileUrl("gradle/libs.versions.toml", ref = "v0.25.1"))
            .isEqualTo("https://raw.githubusercontent.com/varabyte/kobweb/v0.25.1/gradle/libs.versions.toml")
    }

    @Test
    fun `raw file url percent-encodes the path`() {
        val url = GitHubRepo("varabyte/kobweb").rawFileUrl("docs/Getting Started.md")

        assertThat(url).isEqualTo("https://raw.githubusercontent.com/varabyte/kobweb/main/docs/Getting%20Started.md")
    }
}
