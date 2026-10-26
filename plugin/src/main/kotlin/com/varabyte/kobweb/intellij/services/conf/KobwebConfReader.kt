package com.varabyte.kobweb.intellij.services.conf

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile
import com.varabyte.kobweb.intellij.util.kobweb.conf.findKobwebConf
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteRedirect

/**
 * An application service which reads values out of a Kobweb site's `.kobweb/conf.yaml` file (see [findKobwebConf]).
 *
 * Reading YAML requires IntelliJ's YAML plugin, which the Kobweb plugin only optionally depends on, so this is only
 * registered when it's enabled (see `kobweb-withYaml.xml`). Use [getInstance] to fetch it, which returns null
 * otherwise. (In practice, this is probably unlikely to happen to most users, outside of those who actively turned it
 * off.)
 */
interface KobwebConfReader {
    /**
     * Returns the redirects listed under `server.redirects`, in order. Redirects that are missing their `from` or `to`
     * value are skipped.
     */
    fun readRedirects(conf: PsiFile): List<RouteRedirect>

    companion object {
        fun getInstance(): KobwebConfReader? =
            ApplicationManager.getApplication().getService(KobwebConfReader::class.java)
    }
}
