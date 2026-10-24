package com.varabyte.kobweb.intellij.test.fixtures

import com.varabyte.kobweb.intellij.model.KobwebProjectType

/** A test case for code inside a Kobweb application module (e.g. a site). */
abstract class KobwebApplicationTestCase : KobwebProjectTestCase(KobwebProjectType.Application) {
    override fun setUp() {
        super.setUp()

        // Newer versions of Kobweb have this annotation, but the version we test against doesn't. Delete this once it
        // does, but note that tests for APIs we recognize without the annotation (e.g. `Link`, `ctx.route.path`) will
        // then start testing the annotation instead, so they'll need their own unannotated declarations to keep
        // covering that.
        myFixture.addFileToProject(
            // Placed in the folder matching its package, as otherwise the IDE infers a package prefix for the whole
            // source root from it, which breaks tests that move files
            "com/varabyte/kobweb/core/RoutePath.kt",
            // language=kotlin
            """
            package com.varabyte.kobweb.core

            enum class RoutePathType { URL, PATH_QUERY_AND_FRAGMENT, PATH_ONLY }

            @Target(
                AnnotationTarget.VALUE_PARAMETER,
                AnnotationTarget.PROPERTY,
                AnnotationTarget.PROPERTY_SETTER,
                AnnotationTarget.PROPERTY_GETTER,
            )
            annotation class RoutePath(val type: RoutePathType = RoutePathType.URL, val mustExist: Boolean = true)
            """.trimIndent()
        )
    }
}
