package com.varabyte.kobweb.intellij.test.fixtures

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.util.PathUtil
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.services.project.KobwebProjectCacheService
import com.varabyte.kobweb.intellij.util.kobweb.KobwebPluginState
import com.varabyte.kobweb.intellij.util.kobweb.kobwebPluginState
import java.io.File

abstract class KobwebApplicationTestCase : BasePlatformTestCase() {
    companion object {
        // Provides JDK and Kotlin calls which we can use in tests
        private val KOTLIN_JDK_11 = object : DefaultLightProjectDescriptor() {
            override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk11()

            override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
                super.configureModule(module, model, contentEntry)
                model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_11

                // KotlinVersion lives in kotlin-stdlib, so this locates the stdlib jar on the test classpath
                val stdlibJar = File(PathUtil.getJarPathForClass(KotlinVersion::class.java))
                PsiTestUtil.addLibrary(model, "kotlin-stdlib", stdlibJar.parent, stdlibJar.name)
            }
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KOTLIN_JDK_11
    }

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
            // Not actually part of the Kotlin APIs but useful for our own stubs.
            // Use it anytime the implementation of a method is non-trivial.
            "src/stubs/common/Stub.kt",
            // language=kotlin
            """
            package test.stub
            @Suppress("NOTHING_TO_INLINE", "FunctionName", "UNCHECKED_CAST")
            inline fun <T> STUB() = Any() as T
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/js/Dom.kt",
            // language=kotlin
            """
                 package org.w3c.dom

                 interface Element
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/android/Compose.kt",
            // language=kotlin
            """
                package androidx.compose.runtime
                import kotlin.reflect.KProperty

                @Target(
                    AnnotationTarget.FUNCTION,
                    AnnotationTarget.TYPE,
                    AnnotationTarget.TYPE_PARAMETER,
                    AnnotationTarget.PROPERTY_GETTER,
                )
                annotation class Composable

                interface State<out T> {
                    val value: T
                }
                interface MutableState<T> : State<T> {
                    override var value: T
                }

                @Suppress("NOTHING_TO_INLINE")
                inline operator fun <T> State<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value

                @Suppress("NOTHING_TO_INLINE")
                inline operator fun <T> MutableState<T>.setValue(
                    thisObj: Any?,
                    property: KProperty<*>,
                    value: T,
                ) {
                    this.value = value
                }
                """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/composehtml/Attr.kt",
            // language=kotlin
            """
                 package org.jetbrains.compose.web.attributes
                 import org.w3c.dom.Element

                 interface AttrsScope<out E : Element> {
                     fun attr(attr: String, value: String)
                 }
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/composehtml/Style.kt",
            // language=kotlin
            """
                package org.jetbrains.compose.web.css
                import test.stub.STUB

                interface StylePropertyValue
                interface StylePropertyNumber: StylePropertyValue
                interface StylePropertyString: StylePropertyValue
                interface StylePropertyEnum: StylePropertyString

                interface CSSStyleValue: StylePropertyValue {
                    override fun toString(): String
                }

                @Suppress("NOTHING_TO_INLINE", "FunctionName")
                inline fun StylePropertyValue(value: String) = STUB<StylePropertyString>()
                @Suppress("NOTHING_TO_INLINE", "FunctionName")
                inline fun StylePropertyValue(value: Number) = STUB<StylePropertyNumber>()
                @Suppress("NOTHING_TO_INLINE", "FunctionName")
                inline fun CSSStyleValue(value: String) = STUB<CSSStyleValue>()

                interface StyleScope {
                    fun property(name: String, value: StylePropertyValue)
                    fun property(name: String, value: String)
                }
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/composehtml/Units.kt",
            // language=kotlin
            """
                 package org.jetbrains.compose.web.css
                 import test.stub.STUB

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
                     @Suppress("ClassName")
                     interface percent: CSSUnitPercentage
                     @Suppress("ClassName")
                     interface em: CSSUnitRel
                     @Suppress("ClassName")
                     interface rem: CSSUnitRel
                     @Suppress("ClassName")
                     interface px: CSSUnitAbs

                     companion object {
                        inline val percent get() = STUB<percent>()
                        inline val em get() = STUB<em>()
                        inline val rem get() = STUB<rem>()
                        inline val px get() = STUB<px>()
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
            "src/stubs/composehtml/Color.kt",
            // language=kotlin
            """
                 package org.jetbrains.compose.web.css
                 import org.jetbrains.compose.web.css.StylePropertyValue
                 interface CSSColorValue : StylePropertyValue
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/Units.kt",
            // language=kotlin
            """
                 package com.varabyte.kobweb.compose.css

                 import org.jetbrains.compose.web.css.CSSNumericValue
                 import org.jetbrains.compose.web.css.CSSUnitLengthOrPercentage
                 import org.jetbrains.compose.web.css.CSSUnitLength

                 typealias CSSLengthOrPercentageNumericValue = CSSNumericValue<out CSSUnitLengthOrPercentage>
                 typealias CSSLengthNumericValue = CSSNumericValue<out CSSUnitLength>
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/Modifier.kt",
            // language=kotlin
            """
                 package com.varabyte.kobweb.compose.ui
                 interface Modifier {
                    interface Element : Modifier
                    companion object : Modifier
                 }
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/WebModifier.kt",
            // language=kotlin
            """
                 package com.varabyte.kobweb.compose.ui
                 import org.jetbrains.compose.web.attributes.AttrsScope
                 import org.jetbrains.compose.web.css.StyleScope

                 interface WebModifier : Modifier.Element

                 interface AttrsModifier : WebModifier
                 interface StyleModifier : WebModifier

                 @Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
                 fun Modifier.attrsModifier(attrs: (AttrsScope<*>.() -> Unit)): Modifier = Modifier
                 @Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
                 fun Modifier.styleModifier(styles: (StyleScope.() -> Unit)): Modifier = Modifier
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/Colors.kt",
            // language=kotlin
            """
                package com.varabyte.kobweb.compose.ui.graphics
                import org.jetbrains.compose.web.css.CSSColorValue
                import test.stub.STUB

                interface Color : CSSColorValue

                object Colors {
                    val Black: Color = STUB()
                    val White: Color = STUB()
                    val Red: Color = STUB()
                    val Orange: Color = STUB()
                    val Yellow: Color = STUB()
                    val Green: Color = STUB()
                    val Blue: Color = STUB()
                    val Purple: Color = STUB()
                    val Magenta: Color = STUB()
                }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/ColorMode.kt",
            // language=kotlin
            """
            package com.varabyte.kobweb.silk.theme.colors
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.MutableState
            import test.stub.STUB

            enum class ColorMode {
                LIGHT,
                DARK;
            
                companion object {
                    val currentState: MutableState<ColorMode> @Composable get() = STUB()
                    val current: ColorMode @Composable get() = STUB()
                }
            
                val isLight get() = (this == LIGHT)
                val isDark get() = (this == DARK)
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/CssStyle.kt",
            // language=kotlin
            """
                 @file:Suppress("UNUSED_PARAMETER")
                 package com.varabyte.kobweb.silk.style
                 import androidx.compose.runtime.Composable
                 import com.varabyte.kobweb.compose.ui.Modifier
                 import com.varabyte.kobweb.silk.theme.colors.ColorMode

                 sealed interface CssKind
                 sealed interface GeneralKind : CssKind
                 sealed interface RestrictedKind : CssKind
                 interface ComponentKind : CssKind

                 interface CssStyleScopeBase {
                    val colorMode: ColorMode
                 }

                 abstract class StyleScope {
                    fun base(createModifier: () -> Modifier) {}
                 }

                 class CssStyleScope(override val colorMode: ColorMode) : CssStyleScopeBase, StyleScope()
                 class CssStyleBaseScope(override val colorMode: ColorMode) : CssStyleScopeBase

                 abstract class CssStyle<K : CssKind> {
                    companion object // for extensions
                 }

                 @Suppress("FunctionName")
                 fun CssStyle(
                     extraModifier: @Composable () -> Modifier = { Modifier },
                     init: CssStyleScope.() -> Unit
                 ) = object : CssStyle<GeneralKind>() {}

                 fun CssStyle.Companion.base(
                     extraModifier: @Composable () -> Modifier = { Modifier },
                     init: CssStyleBaseScope.() -> Modifier
                 ) = object : CssStyle<GeneralKind>() {}

                 @Suppress("FunctionName")
                 fun <K : ComponentKind> CssStyle(
                     extraModifier: @Composable () -> Modifier = { Modifier },
                     init: CssStyleScope.() -> Unit
                 ) = object : CssStyle<K>() {}

                 fun <K : ComponentKind> CssStyle.Companion.base(
                     extraModifier: @Composable () -> Modifier = { Modifier },
                     init: CssStyleBaseScope.() -> Modifier
                 ) = object : CssStyle<K>() {}

                 @Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
                 fun CssStyle<GeneralKind>.extendedBy(
                     extraModifier: @Composable () -> Modifier = { Modifier },
                     init: CssStyleScope.() -> Unit
                 ) = object : CssStyle<GeneralKind>() {}

                 @Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
                 fun CssStyle<GeneralKind>.extendedByBase(
                     extraModifier: @Composable () -> Modifier = { Modifier },
                     init: CssStyleBaseScope.() -> Modifier
                 ) = object : CssStyle<GeneralKind>() {}

                 @Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
                 @Composable
                 fun CssStyle<GeneralKind>.toModifier(): Modifier = Modifier

                 @Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
                 @Composable
                 fun CssStyle<RestrictedKind>.toModifier(): Modifier = Modifier

                 @Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
                 @Composable
                 fun Iterable<CssStyle<GeneralKind>>.toModifier(): Modifier = Modifier
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/CssStyleVariant.kt",
            // language=kotlin
            """
                @file:Suppress("UNUSED_PARAMETER")
                package com.varabyte.kobweb.silk.style
                import androidx.compose.runtime.Composable
                import com.varabyte.kobweb.compose.ui.Modifier

                class CssStyleVariant<K : ComponentKind>

                fun <K : ComponentKind> CssStyle<K>.addVariant(
                    extraModifier: @Composable () -> Modifier = { Modifier },
                    init: CssStyleScope.() -> Unit
                ): CssStyleVariant<K> = CssStyleVariant<K>()

                fun <K : ComponentKind> CssStyle<K>.addVariantBase(
                    extraModifier: @Composable () -> Modifier = { Modifier },
                    init: CssStyleBaseScope.() -> Modifier
                ): CssStyleVariant<K> = CssStyleVariant<K>()

                fun <K : ComponentKind> CssStyleVariant<K>.extendedBy(
                    extraModifier: @Composable () -> Modifier = { Modifier },
                    init: CssStyleScope.() -> Unit
                ): CssStyleVariant<K> = CssStyleVariant<K>()


                @Suppress("UnusedReceiverParameter")
                fun <K : ComponentKind> CssStyleVariant<K>.extendedByBase(
                    extraModifier: @Composable () -> Modifier = { Modifier },
                    init: CssStyleBaseScope.() -> Modifier
                ) = CssStyleVariant<K>()

                @Suppress("UnusedReceiverParameter")
                @Composable
                fun <K : ComponentKind> CssStyle<K>.toModifier(vararg variants: CssStyleVariant<K>?): Modifier {
                    return Modifier
                }
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/Keyframes.kt",
            // language=kotlin
            """
                @file:Suppress("UNUSED_PARAMETER")
                package com.varabyte.kobweb.silk.style.animation
                import com.varabyte.kobweb.silk.style.*
                import com.varabyte.kobweb.compose.ui.Modifier
                import com.varabyte.kobweb.silk.theme.colors.ColorMode

                class KeyframesBuilder(override val colorMode: ColorMode) : CssStyleScopeBase {
                    fun from(createStyle: () -> Modifier) {}
                    fun to(createStyle: () -> Modifier) {}
                }

                class Keyframes(init: KeyframesBuilder.() -> Unit)
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/StyleSelectors.kt",
            // language=kotlin
            """
                 @file:Suppress("UNUSED_PARAMETER")
                 package com.varabyte.kobweb.silk.style.selectors
                 import com.varabyte.kobweb.compose.ui.Modifier
                 import com.varabyte.kobweb.silk.style.StyleScope

                 @Suppress("UnusedReceiverParameter")
                 fun StyleScope.focus(block: () -> Modifier) {}
                 @Suppress("UnusedReceiverParameter")
                 fun StyleScope.hover(block: () -> Modifier) {}
             """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/StyleVariable.kt",
            // language=kotlin
            """
            @file:Suppress("UNUSED_PARAMETER")
            package com.varabyte.kobweb.compose.css
            import kotlin.reflect.KProperty
            import org.jetbrains.compose.web.css.StylePropertyNumber
            import org.jetbrains.compose.web.css.StylePropertyString
            import org.jetbrains.compose.web.css.StylePropertyValue
            import test.stub.STUB

            sealed class StyleVariable<T : StylePropertyValue, V>(name: String, defaultFallback: T?) {
                fun value(): T = STUB<T>()

                class PropertyValue<T : StylePropertyValue>( name: String, defaultFallback: T? = null)
                : StyleVariable<T, T>(name, defaultFallback)

                class NumberValue<T : Number>(name: String, defaultFallback: T? = null)
                : StyleVariable<StylePropertyNumber, T>(name, STUB())

                class StringValue(
                    name: String,
                    defaultFallback: String? = null,
                ) : StyleVariable<StylePropertyString, String>(name, STUB())
            }

            // Support "by" syntax

            class StyleVariablePropertyProvider<T : StylePropertyValue>(private val defaultFallback: T?) {
                operator fun getValue(thisRef: Any?, property: KProperty<*>) =
                    StyleVariable.PropertyValue("stubbed-name", defaultFallback)
            }

            class StyleVariableNumberProvider<T : Number>(private val defaultFallback: T?) {
                operator fun getValue(thisRef: Any?, property: KProperty<*>) =
                    StyleVariable.NumberValue("stubbed-name", defaultFallback)
            }

            class StyleVariableStringProvider(private val defaultFallback: String?) {
                operator fun getValue(thisRef: Any?, property: KProperty<*>) =
                    StyleVariable.StringValue("stubbed-name", defaultFallback)
            }

            @Suppress("FunctionName")
            fun <T : StylePropertyValue> StyleVariable(defaultFallback: T? = null) =
                StyleVariablePropertyProvider(defaultFallback)

            @Suppress("FunctionName")
            fun <T : Number> StyleVariable(defaultFallback: T? = null) =
                StyleVariableNumberProvider(defaultFallback)

            @Suppress("FunctionName", "FINAL_UPPER_BOUND")
            fun <T : String> StyleVariable(defaultFallback: T? = null) =
                StyleVariableStringProvider(defaultFallback)


            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/StyleProperties.kt",
            // language=kotlin
            """
            package com.varabyte.kobweb.compose.css
            import org.jetbrains.compose.web.css.StylePropertyEnum
            import test.stub.STUB

            sealed interface LineStyle: StylePropertyEnum {
                companion object {
                    inline val None get() = STUB<LineStyle>()
                    inline val Hidden get() = STUB<LineStyle>()
                    inline val Dotted get() = STUB<LineStyle>()
                    inline val Dashed get() = STUB<LineStyle>()
                    inline val Solid get() = STUB<LineStyle>()
                    inline val Double get() = STUB<LineStyle>()
                    inline val Groove get() = STUB<LineStyle>()
                    inline val Ridge get() = STUB<LineStyle>()
                    inline val Inset get() = STUB<LineStyle>()
                    inline val Outset get() = STUB<LineStyle>()
                }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "src/stubs/kobweb/WebModifiers.kt",
            // language=kotlin
            $$"""
            @file:Suppress("UNUSED_PARAMETER")
            package com.varabyte.kobweb.compose.ui.modifiers
            import com.varabyte.kobweb.compose.ui.Modifier
            import com.varabyte.kobweb.compose.ui.styleModifier
            import com.varabyte.kobweb.compose.ui.attrsModifier
            import com.varabyte.kobweb.compose.css.*
            import org.jetbrains.compose.web.css.*
            import test.stub.STUB

            //-------------------------------
            // Attrs
            //-------------------------------

            @Suppress("UnusedReceiverParameter")
            fun Modifier.id(value: String) = attrsModifier {
                attr("id", value)
            }
            @Suppress("UnusedReceiverParameter")
            fun Modifier.tabIndex(value: Int) = attrsModifier {
                attr("tabindex", value.toString())
            }

            //-------------------------------
            // Styles
            //-------------------------------

            @Suppress("UnusedReceiverParameter")
            fun Modifier.accentColor(value: CSSColorValue) = styleModifier {
                property("accent-color", value)
            }

            @Suppress("UnusedReceiverParameter")
            fun Modifier.backgroundColor(value: CSSColorValue) = styleModifier {
                property("background-color", value)
            }

            @Suppress("UnusedReceiverParameter")
            fun Modifier.border(
                width: CSSLengthNumericValue? = null,
                style: LineStyle? = null,
                color: CSSColorValue? = null,
            ) = styleModifier {
                property("border", STUB<String>())
            }

            @Suppress("UnusedReceiverParameter")
            fun Modifier.borderRadius(value: CSSLengthNumericValue) = styleModifier {
                property("border-radius", value)
            }

            @Suppress("UnusedReceiverParameter")
            fun Modifier.color(value: CSSColorValue) = styleModifier {
                property("color", value)
            }

            @Suppress("UnusedReceiverParameter")
            fun Modifier.fillMaxHeight() = height(100.percent)

            @Suppress("UnusedReceiverParameter")
            fun Modifier.fillMaxWidth() = width(100.percent)

            @Suppress("UnusedReceiverParameter")
            fun Modifier.fontFamily(vararg values: String): Modifier = styleModifier {
                property("font-family", STUB<String>())
            }

            @Suppress("UnusedReceiverParameter")
            fun Modifier.fontSize(values: CSSLengthNumericValue): Modifier = styleModifier {
                property("font-size", STUB<String>())
            }

            @Suppress("UnusedReceiverParameter")
            fun Modifier.height(value: CSSLengthOrPercentageNumericValue) = styleModifier {
                property("height", value)
            }

            @Suppress("UnusedReceiverParameter")
            fun Modifier.margin(
                top: CSSLengthOrPercentageNumericValue = 0.px,
                right: CSSLengthOrPercentageNumericValue = 0.px,
                bottom: CSSLengthOrPercentageNumericValue = 0.px,
                left: CSSLengthOrPercentageNumericValue = 0.px,
            ): Modifier = styleModifier {
                property("margin", "$top $right $bottom $left")
            }

            @Suppress("UnusedReceiverParameter")
            fun Modifier.padding(
                top: CSSLengthOrPercentageNumericValue = 0.px,
                right: CSSLengthOrPercentageNumericValue = 0.px,
                bottom: CSSLengthOrPercentageNumericValue = 0.px,
                left: CSSLengthOrPercentageNumericValue = 0.px,
            ): Modifier = styleModifier {
                property("padding", "$top $right $bottom $left")
            }

            @Suppress("UnusedReceiverParameter")
            fun <T : StylePropertyValue> Modifier.setVariable(variable: StyleVariable.PropertyValue<T>, value: T?): Modifier = this
            @Suppress("UnusedReceiverParameter")
            fun <T : Number> Modifier.setVariable(variable: StyleVariable.NumberValue<T>, value: T?): Modifier = this
            @Suppress("UnusedReceiverParameter")
            fun Modifier.setVariable(variable: StyleVariable.StringValue, value: String?): Modifier = this

            @Suppress("UnusedReceiverParameter")
            fun Modifier.width(value: CSSLengthOrPercentageNumericValue) = styleModifier {
                property("width", value)
            }

            @Suppress("UnusedReceiverParameter")
            fun Modifier.willChange(vararg values: String) = styleModifier {
                property("will-change", STUB<String>())
            }
            """.trimIndent()
        )
    }
}
