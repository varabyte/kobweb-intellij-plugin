package com.varabyte.kobweb.intellij.test.util

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
 * Returns the PSI element actually at the caret offset.
 *
 * This is in contrast to [getElementAtCaret][CodeInsightTestFixture.getElementAtCaret], which returns the _resolved_ element pointed to by the caret.
 */
val CodeInsightTestFixture.elementUnderCaret: PsiElement get() = file.findElementAt(caretOffset) ?: error("No element found under caret offset")
