package com.varabyte.kobweb.intellij.util.idea.swing

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.Cell
import org.intellij.lang.annotations.Language
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

/** Shows [code] (Kotlin) in a popup below this component while the mouse is over it. */
fun JComponent.installCodeTooltip(project: Project?, @Language("kotlin") code: String) {
    var activePopup: JBPopup? = null
    addMouseListener(object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) {
            if (activePopup?.isVisible == true) return

            val codeDisposable = Disposer.newDisposable()
            // disposable should be set before createPanel is called
            val codeTextField = KotlinCodeTextField(code, project, codeDisposable)

            activePopup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(codeTextField, null)
                .setRequestFocus(false)
                .setCancelOnClickOutside(true)
                .setCancelOnOtherWindowOpen(true)
                .createPopup()
                .also { popup ->
                    Disposer.register(popup, codeDisposable)
                    popup.show(RelativePoint.getSouthOf(this@installCodeTooltip))
                }
        }

        override fun mouseExited(e: MouseEvent) {
            activePopup?.cancel()
            activePopup = null
        }
    })
}

/** Shows [code] (Kotlin) in a popup below this cell's component while the mouse is over it. */
fun <T : JComponent> Cell<T>.installCodeTooltip(project: Project?, @Language("kotlin") code: String): Cell<T> {
    component.installCodeTooltip(project, code)
    return this
}
