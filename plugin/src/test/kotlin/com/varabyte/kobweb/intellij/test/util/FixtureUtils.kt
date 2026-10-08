package com.varabyte.kobweb.intellij.test.util

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
 * Returns the PSI element actually at the caret offset.
 *
 * This is in contrast to [getElementAtCaret][CodeInsightTestFixture.getElementAtCaret], which returns the _resolved_ element pointed to by the caret.
 */
val CodeInsightTestFixture.elementUnderCaret: PsiElement get() = file.findElementAt(caretOffset) ?: error("No element found under caret offset")

/**
 * Configure the target file by text and ensure there are no highlighting errors.
 *
 * I almost always want to do these two operations together so I've decided to support it via this convenience method.
 */
fun CodeInsightTestFixture.configureByTextAndHighlight(fileName: String, text: String) {
    configureByText(fileName, text)
    checkHighlighting()
}