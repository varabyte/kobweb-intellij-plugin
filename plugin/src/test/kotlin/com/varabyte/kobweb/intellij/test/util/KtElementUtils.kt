package com.varabyte.kobweb.intellij.test.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtElement
import java.util.concurrent.CompletableFuture

/**
 * Like [analyze] but works on the EDT, which is useful for tests.
 *
 * This can block the EDT, but unlike in production, we don't worry too much about the user experience because we're
 * headless anyway.
 */
inline fun <R> analyzeOnEdt(element: KtElement, crossinline action: KaSession.() -> R): R {
    val future = CompletableFuture<R>()

    ApplicationManager.getApplication().executeOnPooledThread {
        try {
            val result = runReadAction {
                analyze(element) {
                    action()
                }
            }
            future.complete(result)
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }
    return future.get()
}
