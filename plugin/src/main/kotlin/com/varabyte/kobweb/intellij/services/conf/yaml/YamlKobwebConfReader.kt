package com.varabyte.kobweb.intellij.services.conf.yaml

import com.intellij.psi.PsiFile
import com.varabyte.kobweb.intellij.services.conf.KobwebConfReader
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteRedirect
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

/**
 * Reads a Kobweb site's `.kobweb/conf.yaml` file using IntelliJ's YAML plugin.
 *
 * NOTE: This is the only place that should refer to the YAML plugin's classes, as it's only loaded when that plugin is
 * enabled.
 */
internal class YamlKobwebConfReader : KobwebConfReader {
    override fun readRedirects(conf: PsiFile): List<RouteRedirect> {
        val yamlFile = conf as? YAMLFile ?: return emptyList()
        val redirects = YAMLUtil.getQualifiedKeyInFile(yamlFile, "server", "redirects")?.value as? YAMLSequence
        return redirects?.items.orEmpty().mapNotNull { item ->
            val redirect = item.value as? YAMLMapping ?: return@mapNotNull null
            fun read(key: String) = (redirect.getKeyValueByKey(key)?.value as? YAMLScalar)?.textValue
            RouteRedirect(
                read("from") ?: return@mapNotNull null,
                read("to") ?: return@mapNotNull null,
                source = redirect,
            )
        }
    }
}
