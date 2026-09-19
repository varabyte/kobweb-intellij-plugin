package com.varabyte.kobweb.intellij.util.ux

import com.intellij.openapi.util.IconLoader
import com.varabyte.kobweb.intellij.project.KobwebProject
import javax.swing.Icon

object UxGlobals {
    val gutterIcon: Icon by lazy {
        IconLoader.getIcon("/assets/icons/kobweb16.svg", KobwebProject::class.java)
    }
}
