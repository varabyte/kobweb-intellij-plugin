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

/**
 * Check the target file for expected text and ensure there are no highlighting errors.
 *
 * Doing both of these operations together allows us to make sure that the code we generate didn't accidentally
 * introduce any highlighting errors.
 */
fun CodeInsightTestFixture.checkResultAndHighlight(expected: String) {
    val pattern = setOf("warning", "error").joinToString("|")

    // Search for tags like <warning>, </warning>, and <warning blahblahblah>
    val regex = Regex("</?($pattern)(\\s+[^>]*)?>")

    // NOTE: checkResult expects EXACT text match, with no highlighting tags embedded. So remove them first, then
    // refresh the fixture in place with the tagged version if checkResult passes
    val withHighlightTagsStripped = expected.replace(regex, "")
    checkResult(withHighlightTagsStripped)
    configureByTextAndHighlight(file.name, expected)
}