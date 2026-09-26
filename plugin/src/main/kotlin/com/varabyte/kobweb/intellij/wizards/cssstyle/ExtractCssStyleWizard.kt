package com.varabyte.kobweb.intellij.wizards.cssstyle
//
//
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.codeStyle.IndentHelper
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.ui.JBUI
import com.varabyte.kobweb.intellij.settings.KobwebAppSettingsService
import com.varabyte.kobweb.intellij.util.compose.STYLE_PROPERTY_VALUE_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.style.CSS_STYLE_SUFFIX
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleNameWarningValidator
import com.varabyte.kobweb.intellij.util.kobweb.style.createStyleNameErrorValidator
import com.varabyte.kobweb.intellij.wizards.SimpleWizard
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.isTopLevel
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance
import javax.swing.JComponent
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.sequences.forEach
import kotlin.text.appendLine

/**
 * Extracted information about some target `Modifier.a(...).b(...).c(...)` chain.
 */
class ModifierChainInfo(val entries: List<Entry>) {
    class Entry(
        val webModifier: KtNamedFunction,
        val webModifierType: WebModifierType,
        val parameters: List<Parameter>,
    ) {
        class Parameter(
            val name: String,
            val type: Type,
            val value: Value?,
        ) {
            class Value(
                val text: String,
                val isGlobal: Boolean,
            )

            class Type(
                /**
                 * All import paths needed by this type.
                 *
                 * This is usually a single value, but it can be multiple values if the target type is a generic value, as
                 * some of the types represent generic parameters.
                 */
                val imports: Set<String>,
                val rendered: String,
            )

            /**
             * If true, it means this parameter is bound to a variable or method that comes from a local scope.
             *
             * For example, this means if we refactor out this parameter into a CssStyle, we will need to create an
             * accompanying StyleVariable with it, to store the extra value.
             */
            fun hasLocalValue() = value != null && !value.isGlobal
        }

        fun hasParameterWithLocalValue(): Boolean {
            return parameters.any { it.hasLocalValue() }
        }
    }
}

private object Keys {
    /**
    * Creates and caches a Key<T> instance using the property's declared name.
    */
    fun <T> key(): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Key<T>>> {
        return PropertyDelegateProvider { _, property ->
            val createdKey = Key.create<T>(property.name)
            ReadOnlyProperty { _, _ -> createdKey }
        }
    }

    fun <T> key(defaultValue: T): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Key<T>>> {
        return PropertyDelegateProvider { _, property ->
            val createdKey = KeyWithDefaultValue.create<T>(property.name, defaultValue)
            ReadOnlyProperty { _, _ -> createdKey }
        }
    }
    val STYLE_NAME by key<String>()
    val MODIFIER_CHAIN_INFO by key<ModifierChainInfo>()
    val USE_CONCISE_SYNTAX by key<Boolean>()
    val EXTRACT_ATTRIBUTES by key<Boolean>()

    val REMEMBER_SYNTAX_CHOICE by key(false)
    val REMEMBER_EXTRACT_ATTRIBUTES_CHOICE by key(false)
    val SKIP_ATTRIBUTE_WARNING by key(false)
}

private tailrec fun KtDotQualifiedExpression.getEntireDotQualifiedExpression(): KtDotQualifiedExpression {
    val parentExpr = parent as? KtDotQualifiedExpression
    return parentExpr?.getEntireDotQualifiedExpression() ?: this
}

private val appSettings = KobwebAppSettingsService.getInstance().state

class ExtractCssStyleWizard(
    project: Project,
    input: Input
) : SimpleWizard<ExtractCssStyleWizard.Input, ExtractCssStyleWizard.Result>(
    project,
    TITLE,
    input,
    size = JBUI.size(400, 520)
) {
    class Input(
        val modifierChainStart: KtDotQualifiedExpression,
        val initialPrefix: String = "My",
    )

    interface Result {
        val styleName: String
        val modifierChainInfo: ModifierChainInfo
        val extractAttributes: Boolean
        val useConciseSyntax: Boolean
    }
    // Only call after all data keys are entered!
    private fun Data.toResult(): Result {
        return object : Result {
            override val styleName = getUserData(Keys.STYLE_NAME)!!
            override val modifierChainInfo = getUserData(Keys.MODIFIER_CHAIN_INFO)!!
            override val extractAttributes = getUserData(Keys.EXTRACT_ATTRIBUTES)!!
            override val useConciseSyntax = getUserData(Keys.USE_CONCISE_SYNTAX)!!
        }
    }

    companion object {
        const val TITLE = "Extract Inline Modifier to CssStyle"
    }

    context(kaSession: KaSession)
    private fun KtDotQualifiedExpression.toModifierChainInfo(): ModifierChainInfo = with(kaSession) {
        val chainedCalls = mutableListOf<Pair<KtNamedFunction, KtCallExpression>>()
        var current: KtDotQualifiedExpression? = this@toModifierChainInfo
        while (current != null) {
            val callExpression = (current.selectorExpression as? KtCallExpression)
            val namedFun = callExpression
                ?.resolveToCall()
                ?.singleFunctionCallOrNull()
                ?.symbol?.psi
                    as? KtNamedFunction
            if (namedFun != null) { chainedCalls.add(namedFun to callExpression) }
            current = PsiTreeUtil.getParentOfType(current, KtDotQualifiedExpression::class.java)
        }

        val entries = chainedCalls.map { (funcDefn, callExpr) ->
            val funcCall = callExpr.resolveToCall()?.singleFunctionCallOrNull()
            val argMapping = funcCall?.argumentMapping ?: emptyMap()

            val parameters = mutableListOf<ModifierChainInfo.Entry.Parameter>()
            if (funcCall != null) {
                parameters.addAll(funcCall.symbol.valueParameters.map { paramSymbol ->
                    val argValueExpr = argMapping.entries
                        .firstOrNull { it.value.symbol == paramSymbol }
                        ?.key
                    // valueArgument includes the full expression, e.g. not just "10" but "value = 10" if the user
                    // included it explicitly
                    val valueArgument = argValueExpr?.findParentOfType<KtValueArgument>()
                    val paramValue = when {
                        valueArgument != null -> {
                            fun KtExpression.getReferencedSimpleNames(): List<KtSimpleNameExpression> {
                                return if (this is KtSimpleNameExpression) listOf(this)
                                else PsiTreeUtil.findChildrenOfType(this, KtSimpleNameExpression::class.java).toList()
                            }

                            val references = argValueExpr.getReferencedSimpleNames()

                            // Check if the parameter is global. If so, we can move the function call to the top-level
                            // CssStyle trivially.
                            val isGlobal = references.all { ref ->
                                // Literals or unresolved names (e.g. '100', 'true') have no symbol
                                val refSymbol = ref.mainReference.resolveToSymbol() ?: return@all true
                                if (refSymbol is KaDeclarationSymbol && refSymbol.isTopLevel) return@all true

                                var containerSymbol = refSymbol.containingSymbol

                                while (containerSymbol != null) {
                                    when (containerSymbol) {
                                        is KaPackageSymbol -> return@all true
                                        is KaClassSymbol if containerSymbol.classKind.isObject -> {
                                            if (containerSymbol.isTopLevel) return@all true
                                            containerSymbol = containerSymbol.containingSymbol
                                        }
                                        else -> return@all false
                                    }
                                }

                                true
                            }

                            ModifierChainInfo.Entry.Parameter.Value(valueArgument.text, isGlobal)
                        }
                        else -> null
                    }

                    @OptIn(KaExperimentalApi::class)
                    val paramType = if (paramSymbol.returnType.symbol != null) {
                        // There HAS to be a better way than this, but I fought the IntelliJ APIs and could not find
                        // a way that worked with generic types, regular types, AND type-alias values. So what I do for
                        // now is "parse" the fqns out of the qualified-name version of the render.
                        val qualifiedRender = paramSymbol.returnType.render(
                            KaTypeRendererForSource.WITH_QUALIFIED_NAMES,
                            position = Variance.INVARIANT
                        )

                        ModifierChainInfo.Entry.Parameter.Type(
                            qualifiedRender.split(Regex("[<>, ]")).filter { it.isNotBlank() && it.trim() !in setOf("*", "in", "out") }.toSet(),
                            paramSymbol.returnType.render(
                                KaTypeRendererForSource.WITH_SHORT_NAMES,
                                position = Variance.INVARIANT
                            )
                        )
                    } else null

                    ModifierChainInfo.Entry.Parameter(
                        paramSymbol.name.asString(),
                        paramType ?: ModifierChainInfo.Entry.Parameter.Type(
                            setOf(STYLE_PROPERTY_VALUE_CLASS_ID.asFqNameString()),
                            STYLE_PROPERTY_VALUE_CLASS_ID.shortClassName.asString()
                        ),
                        paramValue
                    )
                })
            }
            ModifierChainInfo.Entry(
                funcDefn,
                funcDefn.getWebModifierType(),
                parameters
            )
        }

        return ModifierChainInfo(entries)
    }

    override fun createSteps(ctx: SimpleWizard<Input, Result>.StepContext): List<Step> {
        val modifierDisplayText = run {
            // IntelliJ may give us back text that looks like this, since the PSI symbol starts at the
            // element itself, not the beginning of the line:
            // ```
            // Modifier.color(
            //               when (ColorMode.current) {
            //                 ColorMode.LIGHT -> Colors.Black
            //                 ColorMode.DARK -> Colors.White
            //               }
            // ```
            val indent = IndentHelper.getInstance()
                .getIndent(input.modifierChainStart.containingKtFile, input.modifierChainStart.node)
            input.modifierChainStart.getEntireDotQualifiedExpression().text
                .lines()
                .mapIndexed { i, line ->
                    val toDrop = if (i == 0) 0 else {
                        minOf(line.indexOfFirst { !it.isWhitespace() }, indent)
                    }
                    line.drop(toDrop)
                }.joinToString("\n")
        }

        return listOf(
            object : Step {
                private val styleNameErrorValidator = input.modifierChainStart.containingKtFile.createStyleNameErrorValidator()
                private val styleNameWarningValidator = StyleNameWarningValidator()

                override val headerText = "Specify CssStyle Name"

                private val styleNameField = run {
                    val initialName = run {
                        var attempt = 1
                        var name: String
                        do {
                            name = "${ctx.input.initialPrefix}${if (attempt == 1) "" else attempt.toString()}$CSS_STYLE_SUFFIX"
                            attempt++
                        } while (!styleNameErrorValidator.checkInput(name))
                        name
                    }

                    JBTextField(initialName)
                }

                override fun produceComponent(): JComponent {
                    return ctx.utils.components.formBuilder()
                        .addComponent(JBLabel("Target for extraction:"))
                        .addComponentFillVertically(ctx.utils.components.kotlinCode(modifierDisplayText), 8)
                        .addLabeledComponent("Style name:", styleNameField)
                        .panel
                }

                override val initialFocusedComponent = styleNameField

                override fun validate(): ValidationInfo? {
                    return with(ctx.utils.validation) {
                        styleNameErrorValidator.toValidationError(styleNameField)
                            ?: styleNameWarningValidator.toValidationWarning(styleNameField)
                    }
                }

                override fun onNext() {
                    ctx.data.putUserData(Keys.STYLE_NAME, styleNameField.text.trim())

                    // We only need to do this once; don't do it again if, for example, the user pressed back to come
                    // to this step and then pressed OK again. We could actually have calculated this IMMEDIATELY but we
                    // decided to wait until the user confirmed their style name first, so we don't do unnecessary work
                    // that might take a while.
                    if (ctx.data.getUserData(Keys.MODIFIER_CHAIN_INFO) == null) {
                        val modifierChainInfo = runWithModalProgressBlocking(project, "Analyzing user code...") {
                            readAction { analyze(input.modifierChainStart) {
                                input.modifierChainStart.toModifierChainInfo()
                            } }
                        }
                        ctx.data.putUserData(Keys.MODIFIER_CHAIN_INFO, modifierChainInfo)
                    }
                }
            },
            object : Step {
                override val headerText = "Select CssStyle Syntax"

                private val conciseChoice = TabInfo(ctx.utils.components.kotlinCode(
                    """
                        CssStyle.base {
                            Modifier...
                        }
                    """.trimIndent()
                )).setText("Concise")

                private val relaxedChoice = TabInfo(ctx.utils.components.kotlinCode(
                    """
                        CssStyle {
                            base {
                                Modifier...
                            }
                        }
                    """.trimIndent()
                )).setText("Relaxed")

                private val choices = ctx.utils.components.tabs {
                    addTab(conciseChoice)
                    addTab(relaxedChoice)
                }

                private val rememberCheckbox = ctx.utils.components.rememberCheckbox()

                override fun produceComponent(): JComponent {
                    return ctx.utils.components.formBuilder()
                        .addComponent(JBLabel("Would you like to use a concise or relaxed CssStyle syntax?"))
                        .addComponentFillVertically(choices.component, 8)
                        .addComponent(JBLabel(
                            "<html>The <b>concise</b> format is generally recommended as it reduces indentation, unless you plan to add <a href=\"https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Selectors\">additional selectors</a> soon (e.g., <code>hover</code>, <code>focus</code>, <code>link</code>).<br><br>(Do not stress too much about the decision, as you can easily refactor this later.)",
                        ))
                        .addComponent(rememberCheckbox)
                        .panel
                }

                override val initialFocusedComponent = choices.component

                init {
                    ctx.data.putUserData(
                        Keys.USE_CONCISE_SYNTAX,
                        when (appSettings.extractCssStyle.format) {
                            KobwebAppSettingsService.ExtractCssStyle.Format.CONCISE,
                            KobwebAppSettingsService.ExtractCssStyle.Format.ASK_ME -> true
                            KobwebAppSettingsService.ExtractCssStyle.Format.RELAXED -> false
                        }
                    )
                }

                override fun shouldShow(): Boolean {
                    return appSettings.extractCssStyle.format == KobwebAppSettingsService.ExtractCssStyle.Format.ASK_ME
                }

                override fun onNext() {
                    ctx.data.putUserData(Keys.USE_CONCISE_SYNTAX, choices.targetInfo == conciseChoice)
                    ctx.data.putUserData(Keys.REMEMBER_SYNTAX_CHOICE, rememberCheckbox.isSelected)
                }
            },
            object : Step {
                override val headerText = "Extract Attribute Modifier(s)?"

                private val leaveInlineChoice = TabInfo(ctx.utils.components.kotlinCode(
                    """
                        // Style
                        CssStyle { /*...*/ }

                        // Inline Modifier
                        CssStyle.toModifier().id("hi").tabIndex(0)
                        //                    ^^^^^^^^^^^^^^^^^^^^
                    """.trimIndent()
                )).setText("Leave Inline")

                private val extractChoice = TabInfo(ctx.utils.components.kotlinCode(
                    """
                        // Style
                        CssStyle(extraModifier = {
                            Modifier.id("hi").tabIndex(0)
                            //       ^^^^^^^^^^^^^^^^^^^^
                        }) { /*...*/ }

                        // Inline Modifier
                        CssStyle.toModifier()
                    """.trimIndent()
                )).setText("Extract to CssStyle")

                private val choices = ctx.utils.components.tabs {
                    addTab(leaveInlineChoice)
                    addTab(extractChoice)
                }

                private val rememberCheckbox = ctx.utils.components.rememberCheckbox()

                init {
                    ctx.data.putUserData(
                        Keys.EXTRACT_ATTRIBUTES,
                        when (appSettings.extractCssStyle.attributeModifiersStrategy) {
                            KobwebAppSettingsService.ExtractCssStyle.AttributeModifiersStrategy.EXTRACT -> true
                            KobwebAppSettingsService.ExtractCssStyle.AttributeModifiersStrategy.INLINE,
                            KobwebAppSettingsService.ExtractCssStyle.AttributeModifiersStrategy.ASK_ME -> false
                        }
                    )
                }

                override fun shouldShow(): Boolean {
                    if (appSettings.extractCssStyle.attributeModifiersStrategy != KobwebAppSettingsService.ExtractCssStyle.AttributeModifiersStrategy.ASK_ME) return false

                    val modifierChainInfo = ctx.data.getUserData(Keys.MODIFIER_CHAIN_INFO) ?: return false
                    return modifierChainInfo.entries.any { it.webModifierType == WebModifierType.ATTRS }
                }

                override fun produceComponent(): JComponent {
                    return ctx.utils.components.formBuilder()
                        .addComponent(JBLabel(
                            "<html>This modifier chain includes at least one attribute modifier. Would you like to keep attributes declared inline at this call site, or would you like to extract them as well, attaching them to the CssStyle?<br><br>(You can leave it inline if you're not sure.)</html>"
                        ))
                        .addComponentFillVertically(choices.component, 8)
                        .addComponent(rememberCheckbox)
                        .panel
                }

                override val initialFocusedComponent = choices.component

                override fun onNext() {
                    ctx.data.putUserData(Keys.EXTRACT_ATTRIBUTES, choices.targetInfo == extractChoice)
                    ctx.data.putUserData(Keys.REMEMBER_EXTRACT_ATTRIBUTES_CHOICE, rememberCheckbox.isSelected)
                }
            },
            object : Step {
                override val headerText = "Locally Assigned Attribute Modifier(s)"

                private val codeExample = ctx.utils.components.kotlinCode(
                    """
                        private fun produceTabIndex() = 0

                        @Composable
                        fun SomeWidget() {
                            val id = "my-id"
                            // The following example attribute
                            // modifiers can't be extracted due to
                            // assignments tied to the local scope.
                            MyStyle.toModifier()
                                .id(id)
                                //  ^^
                                .tabIndex(produceTabIndex())
                                //        ^^^^^^^^^^^^^^^^^
                        }
                    """.trimIndent()
                )

                private val doNotShowCheckbox = ctx.utils.components.doNotShowAgainCheckbox()

                override fun shouldShow(): Boolean {
                    if (!appSettings.extractCssStyle.showAttributeWarning) return false

                    val modifierChainInfo = ctx.data.getUserData(Keys.MODIFIER_CHAIN_INFO) ?: return false
                    val extractAttrModifiers = ctx.data.getUserData(Keys.EXTRACT_ATTRIBUTES) ?: return false

                    return (extractAttrModifiers && modifierChainInfo.entries.any { it.webModifierType == WebModifierType.ATTRS && it.hasParameterWithLocalValue() })
                }

                override fun produceComponent(): JComponent {
                    return ctx.utils.components.formBuilder()
                        .addComponent(JBLabel(
                            "<html>This modifier chain includes at least one attribute modifier set to a local variable or method result, which means it cannot be extracted and will be left behind, inline.<br><br>This is probably fine!",
                        ))
                        .addComponentFillVertically(codeExample, 8)
                        .addComponent(doNotShowCheckbox)
                        .panel
                }

                override fun onNext() {
                    ctx.data.putUserData(Keys.SKIP_ATTRIBUTE_WARNING, doNotShowCheckbox.isSelected)
                }
            },
            object : Step {
                override val headerText = "Summary"

                private val codeSummary = ctx.utils.components.kotlinCode()

                override fun produceComponent(): JComponent {
                    return ctx.utils.components.formBuilder()
                        .addComponent(JBLabel("<html>On pressing <b>Finish</b>, the following code will be generated:<html>"))
                        .addComponentFillVertically(codeSummary, 8)
                        .panel
                }

                override fun onEntering() {
                    val codeGen = ExtractCssCodeGenerator(ctx.data.toResult())
                    codeSummary.text = buildString {
                        appendLine("// BEFORE ---------------------------------------")
                        appendLine(modifierDisplayText)
                        appendLine()
                        appendLine("// AFTER ----------------------------------------")
                        appendLine("// CssStyle")
                        codeGen.appendCssStyleDeclarationInto(this)
                        appendLine("// Inline Modifier")
                        codeGen.appendInlineModifierInto(this)
                    }
                }
            }
        )
    }

    override fun onFinished(data: Data): Result {
        val result = data.toResult()
        if (data.getUserData(Keys.REMEMBER_SYNTAX_CHOICE)!!) {
            appSettings.extractCssStyle.format = if (result.useConciseSyntax) {
                KobwebAppSettingsService.ExtractCssStyle.Format.CONCISE
            } else KobwebAppSettingsService.ExtractCssStyle.Format.RELAXED
        }
        if (data.getUserData(Keys.REMEMBER_EXTRACT_ATTRIBUTES_CHOICE)!!) {
            appSettings.extractCssStyle.attributeModifiersStrategy = if (result.extractAttributes) {
                KobwebAppSettingsService.ExtractCssStyle.AttributeModifiersStrategy.EXTRACT
            } else KobwebAppSettingsService.ExtractCssStyle.AttributeModifiersStrategy.INLINE
        }
        if (data.getUserData(Keys.SKIP_ATTRIBUTE_WARNING)!!) {
            appSettings.extractCssStyle.showAttributeWarning = false
        }

        return result
    }
}

private class ExtractCssCodeGenerator(val result: ExtractCssStyleWizard.Result) {
    // The full names of StyleVariable names that will be associated with parameters that need them (i.e., because
    // they are bound to a local value from the original scope). If no entry is found for the target parameter,
    // there is no declared style variable for it, and it should just copy its contents over as is.
    private fun ModifierChainInfo.Entry.associatedStyleVariableNames(): Map<ModifierChainInfo.Entry.Parameter, String> {
        if (this.webModifierType != WebModifierType.STYLE) return emptyMap()
        // e.g. `Modifier.backgroundColor(colorVar)` + `val MyStyle = CssStyle { ... }`
        //      --> MyStyle_BackgroundColorVar
        val multiParameterModifier = parameters.size > 1
        return parameters.filter { it.value != null && !it.value.isGlobal }.associateWith { param ->
            buildString {
                append(result.styleName)
                append('_')
                append(webModifier.name!!.capitalized())
                if (multiParameterModifier) {
                    append(param.name.capitalized())
                }
                append("Var")
            }
        }
    }

    private fun ModifierChainInfo.extractStyleVariables(): Map<ModifierChainInfo.Entry.Parameter, String> {
        val self = this
        return mutableMapOf<ModifierChainInfo.Entry.Parameter, String>().apply {
            self.entries.forEach { entry ->
                this.putAll(entry.associatedStyleVariableNames())
            }
        }
    }

    private fun ModifierChainInfo.Entry.toText() = buildString {
        append(webModifier.name!!)
        append('(')
        val varNames = associatedStyleVariableNames()

        append(parameters.filter { varNames.containsKey(it) || it.value != null }.joinToString(", ") { p ->
            varNames[p]?.let { varName -> "${varName}.value()" } ?: p.value!!.text
        })
        append(')')
    }

    private fun ExtractCssStyleWizard.Result.imports(): List<FqName> {
        val importsBuilder = mutableSetOf<String>()
        importsBuilder.add("com.varabyte.kobweb.silk.style.CssStyle")
        val styleVariables = modifierChainInfo.extractStyleVariables()
        if (styleVariables.isNotEmpty()) {
            importsBuilder.add("com.varabyte.kobweb.compose.css.StyleVariable")
            styleVariables.forEach { (parameter, _) ->
                parameter.type.imports.forEach { fqn ->
                    importsBuilder.add(fqn)
                }
            }
        }
        return importsBuilder.sorted().map { FqName(it) }
    }

    /**
     * Partition all attribute modifiers into those that should be extracted and those that should be left behind.
     * ```
     * val (attrModifiersToExtract, attrModifiersToLeaveBehind) = partitionAttributeModifiers()
     * ```
     */
    private fun ExtractCssStyleWizard.Result.partitionAttributeModifiers(): Pair<List<ModifierChainInfo.Entry>, List<ModifierChainInfo.Entry>> {
        return modifierChainInfo.entries
            .filter { it.webModifierType == WebModifierType.ATTRS }
            .partition { extractAttributes && !it.hasParameterWithLocalValue() }
    }
    private val partitionedAttributeModifiers = result.partitionAttributeModifiers()

    fun appendImportsInto(sb: StringBuilder, skipImports: Set<FqName>) = with(result) {
        imports()
            .filter { it !in skipImports }
            .forEach { import -> sb.appendLine("import ${import.asString()}") }
    }


    fun appendCssStyleDeclarationInto(sb: StringBuilder) = with(result) {
        val (attrModifiersToExtract, _) = partitionedAttributeModifiers

        // For now, we also include unknown web modifier types, because as long as a modifier isn't mutable attributes,
        // it should be safe to put into a CssStyle. We reserve the right to change this behavior in the future, at
        // which point this would become `it.webModifierType == STYLE`
        val styleModifiersToExtract = modifierChainInfo.entries.filter { it.webModifierType != WebModifierType.ATTRS }

        val modifier = buildString {
            append("Modifier")
            styleModifiersToExtract
                .forEach { modifierEntry ->
                    append('.')
                    append(modifierEntry.toText())
                }
        }

        val extraModifierParam = if (attrModifiersToExtract.isNotEmpty()) {
            buildString {
                append("extraModifier = { Modifier")
                attrModifiersToExtract.forEach { modifierEntry ->
                    append('.')
                    append(modifierEntry.toText())
                }
                append(" }")
            }
        } else null

        with (sb) {
            modifierChainInfo.extractStyleVariables().forEach { (parameter, varName) ->
                appendLine("val $varName by StyleVariable<${parameter.type.rendered}>()")
            }
            append("val $styleName = CssStyle")
            if (useConciseSyntax) {
                append(".base")
                extraModifierParam?.let { append("($it)") }
                appendLine(" {")
                appendLine("\t$modifier")
                appendLine("}")
            } else {
                extraModifierParam?.let { append("($it)") }
                appendLine(" {")
                appendLine("\tbase {")
                appendLine("\t\t$modifier")
                appendLine("\t}")
                appendLine("}")
            }
        }
    }

    fun appendInlineModifierInto(sb: StringBuilder) = with(result) {
        val (_, inlineAttrModifiers) = partitionedAttributeModifiers

        with (sb) {
            append("$styleName.toModifier()")
            inlineAttrModifiers.forEach { modifierEntry ->
                append('.')
                append(modifierEntry.toText())
            }

            modifierChainInfo.extractStyleVariables().forEach { (parameter, varName) ->
                append(".setVariable(")
                append(varName)
                append(", ")
                append(parameter.value!!.text)
                append(")")
            }
        }
    }
}

fun ExtractCssStyleWizard.Result.performRefactoring(
    editor: Editor,
    modifierChainStart: KtDotQualifiedExpression,
) {
    val ktFile = modifierChainStart.containingKtFile
    val project = modifierChainStart.project
    val psiFactory = KtPsiFactory(project)
    val codeGen = ExtractCssCodeGenerator(this)

    val topLevelDeclaration =
        PsiTreeUtil.findFirstParent(modifierChainStart, /* strict = */true) { parent ->
            parent.parent is KtFile
        } as? KtDeclaration

    CommandProcessor.getInstance().executeCommand(
        project,
        {
            val undoRangeMarker = editor.document.createRangeMarker(modifierChainStart.textRange)

            // When we're done with the write action, `Modifier.a.b.c` will become `SomeCssStyle.toModifier()`
            val cssStyleToModifierElementPtr = WriteAction.compute<SmartPsiElementPointer<PsiElement>, Throwable> {
                // First, add imports
                val existingImports = ktFile.importDirectives.mapNotNull { it.importedFqName }.toSet()

                val importStrs = buildString {
                    codeGen.appendImportsInto(this, existingImports)
                }
                val dummyImports = psiFactory.createFile(importStrs).importList!!
                ktFile.importList?.apply {
                    dummyImports.imports.forEach { add(it) }
                } ?: run {
                    ktFile.add(dummyImports)
                }

                // Next, handle the Modifier -> CssStyle extraction
                val dummyFile = psiFactory.createFile(
                    buildString { codeGen.appendCssStyleDeclarationInto(this) }
                )
                val anchor = topLevelDeclaration ?: ktFile.declarations.firstOrNull()

                for (declaration in dummyFile.declarations) {
                    if (anchor != null) {
                        ktFile.addBefore(declaration, anchor)
                        ktFile.addBefore(psiFactory.createNewLine(), anchor)
                    } else {
                        ktFile.add(declaration)
                    }
                }

                tailrec fun KtDotQualifiedExpression.getEntireDotQualifiedExpression(): KtDotQualifiedExpression {
                    val parentExpr = parent as? KtDotQualifiedExpression
                    return parentExpr?.getEntireDotQualifiedExpression() ?: this
                }

                val replacementExpr = psiFactory.createExpression(
                    buildString { codeGen.appendInlineModifierInto(this) }
                )
                SmartPointerManager.getInstance(project).createSmartPsiElementPointer(modifierChainStart.getEntireDotQualifiedExpression().replace(replacementExpr))
            }

            PsiDocumentManager.getInstance(project).apply {
                doPostponedOperationsAndUnblockDocument(editor.document)
                commitDocument(editor.document)
            }

            val redoRangeMarker = editor.document.createRangeMarker(cssStyleToModifierElementPtr.element!!.textRange)

            val action = object : BasicUndoableAction(editor.document) {
                fun RangeMarker.updateCaret() {
                    if (!this.isValid) return
                    // We need to fetch editors dynamically, instead of using our editor property, as a user can
                    // close a file and reopen it and press undo / redo, at which point our captured editor has been
                    // disposed
                    FileEditorManager.getInstance(project).allEditors
                        .asSequence()
                        .filterIsInstance<TextEditor>()
                        .map { it.editor }
                        .filter { !it.isDisposed && it.document == document }
                        .forEach { editor ->
                            editor.caretModel.moveToOffset(startOffset)
                            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                        }
                }
                override fun undo() = undoRangeMarker.updateCaret()
                override fun redo() = redoRangeMarker.updateCaret()
            }.also { it.redo() }

            UndoManager.getInstance(project).undoableActionPerformed(action)
        },
        ExtractCssStyleWizard.TITLE,
        null,
        editor.document
    )
}