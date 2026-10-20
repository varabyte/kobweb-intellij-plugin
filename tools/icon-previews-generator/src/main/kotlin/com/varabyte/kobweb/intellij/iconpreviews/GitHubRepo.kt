package com.varabyte.kobweb.intellij.iconpreviews

import java.net.http.HttpResponse

/**
 * A GitHub repo pinned to a specific commit, so every file read from it comes from the same snapshot.
 *
 * @property name The repo's "owner/name", e.g. "varabyte/kobweb".
 */
class GitHubRepo private constructor(val name: String, val sha: String) {
    companion object {
        /** Resolve a branch, tag, or commit (e.g. "main") to the commit it currently points at. */
        fun resolve(name: String, ref: String): GitHubRepo {
            if (ref.matches(Regex("[0-9a-f]{40}"))) return GitHubRepo(name, ref)

            val request = Http.newRequest("https://api.github.com/repos/$name/commits/$ref")
                .header("Accept", "application/vnd.github.sha")
                .build()
            val response = Http.client.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() == 200) { "Could not resolve $name@$ref (HTTP ${response.statusCode()})" }
            return GitHubRepo(name, response.body().trim())
        }
    }

    fun fileUrl(path: String) = "https://raw.githubusercontent.com/$name/$sha/$path"

    override fun toString() = "$name@$sha"
}
