package com.varabyte.kobweb.intellij.intentions

import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import com.varabyte.kobweb.intellij.test.util.checkResultAndHighlight
import com.varabyte.truthish.assertWithMessage
import org.intellij.lang.annotations.Language

private const val TEST_FILE_NAME = "KobwebIntentionTest.kt"

abstract class KobwebIntentionTestBase : KobwebApplicationTestCase() {
    protected abstract val intentionName: String

    protected fun doTest(
        @Language("kotlin") before: String,
        @Language("kotlin") after: String,
    ) {
        val intentionName = intentionName
        myFixture.configureByText(TEST_FILE_NAME, before)
        myFixture.checkHighlighting()
        val intention = myFixture.getAvailableIntention(intentionName)
            ?: error("Intention \"$intentionName\" was not available for code:\n\n$before")
        myFixture.launchAction(intention)
        myFixture.checkResultAndHighlight(after)
    }

    protected fun assertIntentionNotAvailable(
        @Language("kotlin") code: String,
    ) {
        val intentionName = intentionName
        myFixture.configureByText(TEST_FILE_NAME, code)
        myFixture.checkHighlighting()
        val intention = myFixture.getAvailableIntention(intentionName)
        assertWithMessage("Intention \"$intentionName\" was (unexpectedly) available for code:\n\n$code").that(intention).isNull()
    }

    protected fun assertIntentionIsAvailable(
        @Language("kotlin") code: String,
    ) {
        val intentionName = intentionName
        myFixture.configureByText(TEST_FILE_NAME, code)
        myFixture.checkHighlighting()
        val intention = myFixture.getAvailableIntention(intentionName)
        assertWithMessage("Intention \"$intentionName\" was (unexpectedly) unavailable for code:\n\n$code").that(intention).isNotNull()
    }
}
