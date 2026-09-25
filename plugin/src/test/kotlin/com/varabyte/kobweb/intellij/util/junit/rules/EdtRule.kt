package com.varabyte.kobweb.intellij.util.junit.rules

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

class EdtRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                if (SwingUtilities.isEventDispatchThread()) {
                    base.evaluate()
                } else {
                    val throwableRef = AtomicReference<Throwable?>()
                    SwingUtilities.invokeAndWait {
                        try {
                            base.evaluate()
                        } catch (t: Throwable) {
                            throwableRef.set(t)
                        }
                    }

                    throwableRef.get()?.let { throw it}
                }
            }
        }
    }
}
