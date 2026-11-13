@file:Suppress("FunctionName")

package com.varabyte.kobweb.intellij.inspections.route

import com.varabyte.kobweb.intellij.test.fixtures.KobwebFullStackTestCase
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

class ApiDynamicParamLookupInspectionTest : KobwebFullStackTestCase() {
    // TODO: Once these tests use Kobweb 0.25.2+, where `Request` has `dynamicParams`, test that `params` lookups of
    //  known keys ARE flagged in API endpoints
    @OptIn(KaAllowAnalysisOnEdt::class)
    fun testParamsLookupsAreNotFlaggedWithoutDynamicParams() {
        configureByText(
            Platform.Jvm,
            "api/users/Post.kt",
            """
            package $TEST_GROUP.api.users

            import com.varabyte.kobweb.api.Api
            import com.varabyte.kobweb.api.ApiContext

            @Api("{user}/posts/{post}")
            suspend fun post(ctx: ApiContext) {
                ctx.req.params["post"]
            }
            """.trimIndent()
        )
        myFixture.enableInspections(DynamicParamLookupInspection())
        allowAnalysisOnEdt { myFixture.checkHighlighting() }
    }
}
