@file:Suppress("FunctionName", "RedundantSuspendModifier")

package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.ex.QuickFixWrapper
import com.varabyte.kobweb.intellij.quickfixes.route.RemovePackageMappingQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.RemoveRouteOverrideQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.ReplaceRouteValueQuickFix
import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import kotlin.reflect.KClass

/** Tests for [RedundantRouteInspection] on the routes of API endpoints and streams. */
class RedundantRouteInspectionApiTest : KobwebFullStackTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(RedundantRouteInspection())
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun checkHighlighting(fileName: String, @Language("kotlin") code: String) {
        configureByText(Platform.Jvm, fileName, code)
        allowAnalysisOnEdt {
            myFixture.checkHighlighting(
                /* checkWarnings = */ true,
                /* checkInfos = */ false,
                /* checkWeakWarnings = */ true,
            )
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun applyFix(quickFixClass: KClass<*>, @Language("kotlin") expected: String) {
        allowAnalysisOnEdt {
            val fix = myFixture.getAllQuickFixes().single { quickFixClass.isInstance(QuickFixWrapper.unwrap(it)) }
            myFixture.launchAction(fix)
        }
        myFixture.checkResult(expected)
    }

    fun testApiRouteOverrideMatchingDefaultIsRemoved() {
        checkHighlighting(
            "api/Hello.kt",
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api(<weak_warning descr="Kobweb would generate the same route (\"/api/hello\") without this override, so it's redundant.">"hello"</weak_warning>)
            suspend fun hello(ctx: ApiContext) {
            }
            """.trimIndent()
        )

        applyFix(
            RemoveRouteOverrideQuickFix::class,
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api
            suspend fun hello(ctx: ApiContext) {
            }
            """.trimIndent()
        )
    }

    fun testApiDynamicSegmentNamedAfterFileCanBeShortened() {
        checkHighlighting(
            "api/users/User.kt",
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api(<weak_warning descr="Can be simplified to \"{}\", which Kobweb expands to the same route.">"{user}"</weak_warning>)
            suspend fun user(ctx: ApiContext) {
            }
            """.trimIndent()
        )

        applyFix(
            ReplaceRouteValueQuickFix::class,
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api("{}")
            suspend fun user(ctx: ApiContext) {
            }
            """.trimIndent()
        )
    }

    fun testApiRouteOverridesThatChangeTheRouteAreNotFlagged() {
        checkHighlighting(
            "api/Hello.kt",
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext
            import com.varabyte.kobweb.api.stream.ApiStream

            @Api("greeting")
            suspend fun hello(ctx: ApiContext) {
            }

            @Api("greetings")
            val greetings = ApiStream { ctx -> }
            """.trimIndent()
        )
    }

    fun testRedundantStreamRouteOverrideIsRemoved() {
        checkHighlighting(
            "api/Chat.kt",
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.stream.ApiStream

            @Api(<weak_warning descr="Kobweb would generate the same route (\"chat\") without this override, so it's redundant.">"chat"</weak_warning>)
            val chat = ApiStream { ctx -> }
            """.trimIndent()
        )

        // Streams don't need `@Api` without an override, but some people like marking all their endpoints with it
        applyFix(
            RemoveRouteOverrideQuickFix::class,
            """
            package $TEST_GROUP.api

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.stream.ApiStream

            @Api
            val chat = ApiStream { ctx -> }
            """.trimIndent()
        )
    }

    fun testRedundantApiPackageMappingIsRemoved() {
        checkHighlighting(
            "api/users/PackageMapping.kt",
            """
            <weak_warning descr="Kobweb would use this name for the package anyway, so this package mapping is redundant.">@file:PackageMapping("users")</weak_warning>
            @file:Suppress("unused")

            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.PackageMapping
            """.trimIndent()
        )

        applyFix(
            RemovePackageMappingQuickFix::class,
            """
            @file:Suppress("unused")

            package $TEST_GROUP.api.users
            """.trimIndent()
        )
    }
}
