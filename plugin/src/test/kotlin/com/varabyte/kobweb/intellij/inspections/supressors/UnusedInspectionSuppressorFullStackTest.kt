@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.supressors

import com.intellij.openapi.application.WriteAction
import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalSourceModuleStateModificationEvent
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UnusedSymbolInspection

class UnusedInspectionSuppressorFullStackTest : KobwebFullStackTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(UnusedSymbolInspection())
    }

    fun testApiStreamsRegisteredByGeneratedCodeAreNotReportedAsUnused() {
        configureByText(
            Platform.Jvm,
            "api/Chat.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.stream.ApiStream
            import com.varabyte.kobweb.api.stream.broadcastExcluding

            val chat = ApiStream { ctx ->
                ctx.stream.broadcastExcluding(ctx.text, ctx.stream.id)
            }
            """.trimIndent()
        )
        myFixture.checkHighlighting()
    }

    fun testApiStreamsDeclaredAsObjectsAreNotReportedAsUnused() {
        configureByText(
            Platform.Jvm,
            "api/Echo.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.stream.ApiStream

            val echo = object : ApiStream() {
                override suspend fun onTextReceived(ctx: TextReceivedContext) {
                    ctx.stream.send(ctx.text)
                }
            }
            """.trimIndent()
        )
        myFixture.checkHighlighting()
    }

    fun testApiStreamsAreStillNotReportedAfterEdits() {
        configureByText(
            Platform.Jvm,
            "api/Chat.kt",
            // language=kotlin
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.stream.ApiStream

            val chat = ApiStream { ctx -> ctx.stream.broadcast(ctx.text) }<caret>
            """.trimIndent()
        )
        myFixture.checkHighlighting()

        // Make sure we don't hold onto the analysis session that the first highlighting pass used (e.g. inside a cached
        // value), by invalidating that session and then editing the file (which invalidates any cached values).
        WriteAction.run<Throwable> { project.publishGlobalSourceModuleStateModificationEvent() }
        myFixture.type("\n")
        myFixture.checkHighlighting()
    }

    // Control test, to ensure that our other tests are genuinely suppressing unused warnings
    fun testApiStreamsThatKobwebDoesntRegisterAreReported() {
        configureByText(
            Platform.Jvm,
            "server/Outside.kt",
            // language=kotlin
            """
            package $TEST_GROUP.server

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.stream.ApiStream

            // Outside the api package, without an absolute route override
            val <warning>outsideStream</warning> = ApiStream { ctx -> ctx.stream.send(ctx.text) }

            // Kobweb doesn't currently allow dynamic segments in stream routes
            @Api("/rooms/{room}")
            val <warning>roomStream</warning> = ApiStream { ctx -> ctx.stream.send(ctx.text) }
            """.trimIndent()
        )
        myFixture.checkHighlighting()
    }
}
