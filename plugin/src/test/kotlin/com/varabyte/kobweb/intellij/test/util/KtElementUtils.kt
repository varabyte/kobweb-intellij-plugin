package com.varabyte.kobweb.intellij.test.util

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtElement

/**
 * Like [analyze] but works on the EDT, which is useful for tests.
 *
 * Note that it isn't actually running analyze ON the EDT, but instead it runs it in a background thread but blocks
 * EDT progress until finished.
 *
 * This can block the EDT, but unlike in production, we don't worry too much about the user experience because we're
 * headless anyway.
 */
inline fun <R> analyzeOnEdt(element: KtElement, crossinline action: KaSession.() -> R): R {
    return runOffEdtAndWait {
        analyze(element) {
            action()
        }
    }
}
