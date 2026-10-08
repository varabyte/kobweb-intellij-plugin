package com.varabyte.kobweb.intellij.test.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities

inline fun <R> runOffEdtAndWait(crossinline block: () -> R): R {
    if (!SwingUtilities.isEventDispatchThread()) return block()

    val future = CompletableFuture<R>()

    ApplicationManager.getApplication().executeOnPooledThread {
        try {
            val result = runReadAction {
                block()
            }
            future.complete(result)
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }
    return future.get()
}
