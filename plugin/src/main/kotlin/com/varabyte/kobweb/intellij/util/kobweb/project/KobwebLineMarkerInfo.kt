package com.varabyte.kobweb.intellij.util.kobweb.project

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.ux.UxGlobals

@Suppress("FunctionName") // Intentional factory method style naming
fun <E : PsiElement> KobwebLineMarkerInfo(
    element: E,
    name: String,
    navHandler: GutterIconNavigationHandler<E>,
    textRange: TextRange = element.textRange,
) = LineMarkerInfo<E>(
    element,
    textRange,
    UxGlobals.gutterIcon,
    /* tooltipProvider = */ { name },
    navHandler,
    /* alignment = */ GutterIconRenderer.Alignment.RIGHT,
    /* accessibleNameProvider = */ { name }
)
