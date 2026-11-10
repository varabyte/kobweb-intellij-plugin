package com.varabyte.kobweb.intellij.iconpreviews

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
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

        /**
         * The newest version tag in the repo with this [major] version, e.g. "6.7.2" for 6. Only tags that are plain
         * "major.minor.patch" versions count.
         */
        fun latestVersionTag(name: String, major: Int): String {
            // Lists every tag whose name starts with the given prefix
            val request = Http.newRequest("https://api.github.com/repos/$name/git/matching-refs/tags/$major.").build()
            val response = Http.client.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() == 200) {
                "Could not list $name's $major.x tags (HTTP ${response.statusCode()})"
            }

            val versionRegex = Regex("""(\d+)\.(\d+)\.(\d+)""")
            return Json.parseToJsonElement(response.body()).jsonArray
                .map { it.jsonObject.getValue("ref").jsonPrimitive.content.removePrefix("refs/tags/") }
                .mapNotNull { tag -> versionRegex.matchEntire(tag)?.let { match -> tag to match.groupValues } }
                .maxWithOrNull(
                    compareBy(
                        { (_, parts) -> parts[1].toInt() },
                        { (_, parts) -> parts[2].toInt() },
                        { (_, parts) -> parts[3].toInt() },
                    )
                )
                ?.first
                ?: error("$name has no $major.x version tags")
        }
    }

    /**
     * The URL for downloading the file at [path], which is the file's real name in the repo and gets percent-encoded
     * here, e.g. the spaces in "otfs/Font Awesome 7 Free-Solid-900.otf".
     */
    fun fileUrl(path: String): String =
        // This URI constructor percent-encodes characters that aren't legal in a path, unlike URI(String)
        URI("https", "raw.githubusercontent.com", "/$name/$sha/$path", null).toASCIIString()

    /** The URL for downloading a zip of the whole repo at this commit. */
    fun archiveUrl(): String = "https://github.com/$name/archive/$sha.zip"

    override fun toString() = "$name@$sha"
}
