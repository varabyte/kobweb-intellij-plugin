package com.varabyte.kobweb.intellij.iconpreviews

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest

/** HTTP plumbing shared by everything the generator downloads. */
object Http {
    val client: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    /**
     * Start building a request to [url] that identifies this tool.
     *
     * Some servers require a User-Agent (GitHub's API rejects requests without one), so always start requests here.
     */
    fun newRequest(url: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI(url)).header("User-Agent", "kobweb-intellij-plugin-icon-previews")
}
