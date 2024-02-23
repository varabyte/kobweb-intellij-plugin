package com.varabyte.kobweb.intellij.util.log

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import org.apache.log4j.Level

@Suppress("OVERRIDE_DEPRECATION", "UnstableApiUsage")
private class DisabledLogger : Logger() {
    override fun isDebugEnabled(): Boolean = false
    override fun debug(message: String, t: Throwable?) = Unit
    override fun info(message: String, t: Throwable?) = Unit
    override fun warn(message: String, t: Throwable?) = Unit
    override fun error(message: String, t: Throwable?, vararg details: String) = Unit
    override fun setLevel(message: Level) = Unit
}

/**
 * An aggressive logger that is intended for use during debugging (especially when testing manually installing plugins).
 *
 * IntelliJ makes it relatively easy to create new loggers, but unless you configure build log settings, those logs can
 * easily get swallowed.
 *
 * This logger is never intended to be used in production, so it will only be enabled if the plugin is a snapshot build.
 */
class KobwebDebugLogger {
    companion object {
        val instance by lazy {
            val plugin = PluginId.findId("com.varabyte.kobweb.intellij")?.let { pluginId ->
                PluginManager.getInstance().findEnabledPlugin(pluginId)
            }
            if (plugin?.version?.endsWith("-SNAPSHOT") == true) {
                Logger.getInstance(KobwebDebugLogger::class.java).apply { setLevel(LogLevel.ALL) }
            } else {
                DisabledLogger()
            }
        }
    }
}
