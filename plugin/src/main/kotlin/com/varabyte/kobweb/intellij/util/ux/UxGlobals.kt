package com.varabyte.kobweb.intellij.util.ux

import com.intellij.DynamicBundle
import com.intellij.openapi.util.IconLoader
import com.varabyte.kobweb.intellij.project.KobwebProject
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier
import javax.swing.Icon

@NonNls
private const val BUNDLE_PATH = "bundles.KobwebBundle"

class KobwebBundle internal constructor() {
    private val INSTANCE = DynamicBundle(KobwebBundle::class.java, BUNDLE_PATH)

    fun message(
        key: @PropertyKey(resourceBundle = BUNDLE_PATH) String,
        vararg params: Any
    ): String {
        return INSTANCE.getMessage(key, *params)
    }

    fun lazyMessage(
        @PropertyKey(resourceBundle = BUNDLE_PATH) key: String,
        vararg params: Any
    ): Supplier<String> {
        return INSTANCE.getLazyMessage(key, *params)
    }
}

object UxGlobals {
    val gutterIcon: Icon by lazy {
        IconLoader.getIcon("/assets/icons/kobweb16.svg", KobwebProject::class.java)
    }

    val textBundle by lazy { KobwebBundle() }

    val FAMILY_NAME get() = textBundle.message("kobweb.name")
}
