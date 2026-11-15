package com.varabyte.kobweb.intellij.util.github

import java.net.URI

/**
 * A helper class for interacting with a public GitHub repository.
 *
 * @property name The repo's "owner/name", e.g. "JetBrains/kotlin".
 * @property defaultBranch The branch that file URLs point at unless given another ref.
 */
class GitHubRepo(val name: String, val defaultBranch: String = "main") {
    // Lets specific repos be declared as extension properties, e.g. `val GitHubRepo.Companion.MyRepo`
    companion object

    /** A link to the GitHub page showing the file at [path] at [ref], e.g. a branch, tag, or commit. */
    fun fileUrl(path: String, ref: String = defaultBranch): String = "https://github.com/$name/blob/$ref/$path"

    /**
     * The URL for downloading the file at [path] at [ref], e.g. a branch, tag, or commit.
     *
     * [path] is the file's real name in the repo, which gets percent-encoded here, e.g. the spaces in
     * "docs/Getting Started.md".
     */
    fun rawFileUrl(path: String, ref: String = defaultBranch): String =
        // This URI constructor percent-encodes characters that aren't legal in a path, unlike URI(String)
        URI("https", "raw.githubusercontent.com", "/$name/$ref/$path", null).toASCIIString()

    override fun toString() = name
}
