package com.varabyte.kobweb.intellij.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import com.varabyte.kobweb.intellij.inspections.modifier.DanglingModifierInspection
import com.varabyte.kobweb.intellij.test.fixtures.KobwebApplicationTestCase
import org.intellij.lang.annotations.Language

abstract class KobwebInspectionTestBase : KobwebApplicationTestCase() {
    protected abstract fun produceInspection(): InspectionProfileEntry
    protected fun doTest(@Language("kotlin") code: String) {
        myFixture.enableInspections(produceInspection())
        myFixture.configureByText("KobwebInspectionTest.kt", code)
        myFixture.checkHighlighting(true, false, false)
    }
}
