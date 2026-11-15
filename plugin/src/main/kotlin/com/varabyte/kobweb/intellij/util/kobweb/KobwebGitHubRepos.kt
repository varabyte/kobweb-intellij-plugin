package com.varabyte.kobweb.intellij.util.kobweb

import com.varabyte.kobweb.intellij.util.github.GitHubRepo

private val kobwebRepo = GitHubRepo("varabyte/kobweb")
private val varabyteDataRepo = GitHubRepo("varabyte/data")

val GitHubRepo.Companion.Kobweb get() = kobwebRepo

/** Data that the plugin downloads at runtime, so it can be updated without a plugin release. */
val GitHubRepo.Companion.VarabyteData get() = varabyteDataRepo
