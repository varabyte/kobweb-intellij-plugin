package com.varabyte.kobweb.intellij.test.fixtures

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService
import com.varabyte.kobweb.intellij.util.kobweb.KobwebPluginState
import com.varabyte.kobweb.intellij.util.kobweb.kobwebPluginState

abstract class KobwebApplicationTestCase : BasePlatformTestCase() {
     override fun setUp() {
         super.setUp()
         addKobwebStubs()
         markModuleAsKobweb()
     }

     override fun tearDown() {
         try {
             project.service<KobwebProjectCacheService>().clear()
             project.kobwebPluginState = KobwebPluginState.DISABLED
         } finally {
             super.tearDown()
         }
     }

    private fun markModuleAsKobweb() {
         project.kobwebPluginState = KobwebPluginState.INITIALIZED
         project.service<KobwebProjectCacheService>()
             .add(KobwebProject("test-shell", KobwebProjectType.Application, KobwebProject.Source.Local(module)))
     }

    /** Minimal stand-ins for the parts of the Kobweb framework that the plugin looks up by ClassId / CallableId. */
    private fun addKobwebStubs() {
        myFixture.addFileToProject(
            "src/stubs/js/Stdlib.kt", """
                 package kotlin.js

                 @Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
                 public inline fun <T> Any?.unsafeCast(): T = this as T
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/js/Dom.kt", """
                 package org.w3c.dom

                 interface Element
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/composehtml/Attr.kt", """
                 package org.jetbrains.compose.web.attributes
                 import org.w3c.dom.Element

                 interface AttrsScope<out TElement : Element> {
                    fun attr(attr: String, value: String): AttrsScope<TElement>
                 }
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/composehtml/Style.kt", """
                 package org.jetbrains.compose.web.css
                 interface StylePropertyValue
                 interface StyleScope {
                    fun property(name: String, value: StylePropertyValue)
                 }
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/composehtml/Units.kt", """
                 package org.jetbrains.compose.web.css
                 import kotlin.js.unsafeCast

                 interface CSSNumericValue<T : CSSUnit> : StylePropertyValue
                 interface CSSSizeValue<T : CSSUnit> : CSSNumericValue<T> {
                     val value: Float
                     val unit: T
                 }

                 data class CSSUnitValueTyped<T : CSSUnit>(
                     override val value: Float,
                     override val unit: T
                 ) : CSSSizeValue<T>

                 interface CSSUnitLengthOrPercentage: CSSUnit
                 interface CSSUnitPercentage: CSSUnitLengthOrPercentage
                 interface CSSUnitLength: CSSUnitLengthOrPercentage
                 interface CSSUnitRel : CSSUnitLength
                 interface CSSUnitAbs: CSSUnitLength

                 typealias CSSLengthOrPercentageValue = CSSSizeValue<out CSSUnitLengthOrPercentage>
                 typealias CSSLengthValue = CSSSizeValue<out CSSUnitLength>
                 typealias CSSPercentageValue = CSSSizeValue<out CSSUnitPercentage>
                 typealias CSSUnitValue = CSSSizeValue<out CSSUnit>
                 typealias CSSNumeric = CSSNumericValue<out CSSUnit>

                 interface CSSUnit {
                     interface percent: CSSUnitPercentage
                     interface em: CSSUnitRel
                     interface rem: CSSUnitRel
                     interface px: CSSUnitAbs

                     companion object {
                        inline val percent get() = "%".unsafeCast<percent>()
                        inline val em get() = "em".unsafeCast<em>()
                        inline val rem get() = "rem".unsafeCast<rem>()
                        inline val px get() = "px".unsafeCast<px>()
                    }
                 }

                 val Number.px
                     get(): CSSSizeValue<CSSUnit.px> = CSSUnitValueTyped(this.toFloat(), CSSUnit.px)

                 val Number.percent
                     get() : CSSSizeValue<CSSUnit.percent> = CSSUnitValueTyped(this.toFloat(), CSSUnit.percent)

                 val Number.em
                     get() : CSSSizeValue<CSSUnit.em> = CSSUnitValueTyped(this.toFloat(), CSSUnit.em)

                 val Number.cssRem
                     get(): CSSSizeValue<CSSUnit.rem> = CSSUnitValueTyped(this.toFloat(), CSSUnit.rem)
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/composehtml/Color.kt", """
                 package org.jetbrains.compose.web.css
                 import org.jetbrains.compose.web.css.StylePropertyValue
                 interface CSSColorValue : StylePropertyValue
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/Units.kt", """
                 package com.varabyte.kobweb.compose.css

                 import org.jetbrains.compose.web.css.CSSNumericValue
                 import org.jetbrains.compose.web.css.CSSUnitLengthOrPercentage
                 import org.jetbrains.compose.web.css.CSSUnitLength

                 typealias CSSLengthOrPercentageNumericValue = CSSNumericValue<out CSSUnitLengthOrPercentage>
                 typealias CSSLengthNumericValue = CSSNumericValue<out CSSUnitLength>
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/Modifier.kt", """
                 package com.varabyte.kobweb.compose.ui
                 interface Modifier {
                    interface Element : Modifier
                    companion object : Modifier
                 }
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/WebModifier.kt", """
                 package com.varabyte.kobweb.compose.ui
                 import org.jetbrains.compose.web.attributes.AttrsScope
                 import org.jetbrains.compose.web.css.StyleScope

                 interface WebModifier : Modifier.Element

                 interface AttrsModifier : WebModifier
                 interface StyleModifier : WebModifier

                 @Suppress("UNUSED_PARAMETER")
                 fun Modifier.attrsModifier(attrs: (AttrsScope<*>.() -> Unit)): Modifier = Modifier
                 @Suppress("UNUSED_PARAMETER")
                 fun Modifier.styleModifier(styles: (StyleScope.() -> Unit)): Modifier = Modifier
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/Colors.kt", """
                 package com.varabyte.kobweb.compose.ui.graphics
                 import org.jetbrains.compose.web.css.CSSColorValue

                 interface Color : CSSColorValue

                 object Colors {
                    private val stub = object : Color {}
                    val Red: Color = stub
                    val Green: Color = stub
                    val Blue: Color = stub
                 }
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/WebModifiers.kt", """
                 package com.varabyte.kobweb.compose.ui.modifiers
                 import com.varabyte.kobweb.compose.ui.Modifier
                 import com.varabyte.kobweb.compose.ui.styleModifier
                 import com.varabyte.kobweb.compose.ui.attrsModifier
                 import com.varabyte.kobweb.compose.ui.graphics.Color
                 import com.varabyte.kobweb.compose.css.CSSLengthOrPercentageNumericValue
                 import org.jetbrains.compose.web.css.px
                 import org.jetbrains.compose.web.css.percent

                 // Attrs
                 fun Modifier.id(value: String) = attrsModifier {
                     attr("id", value)
                 }
                 fun Modifier.tabIndex(value: Int) = attrsModifier {
                     attr("tabindex", value.toString())
                 }

                 // Styles
                 fun Modifier.width(value: CSSLengthOrPercentageNumericValue) = styleModifier {
                     property("width", value)
                 }
                 fun Modifier.fillMaxWidth() = width(100.percent)
                 fun Modifier.color(value: Color) = styleModifier {
                     property("color", value)
                 }
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/CssStyle.kt", """
                 package com.varabyte.kobweb.silk.style
                 import com.varabyte.kobweb.compose.ui.Modifier
                 // TODO: Add CssStyle stubs here
             """.trimIndent()
        )
    }
 }
