package com.varabyte.kobweb.intellij.wizards.cssstyle

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
import com.varabyte.kobweb.intellij.util.idea.key
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierChainingExtension
import com.varabyte.kobweb.intellij.util.kobweb.style.CSS_STYLE_SUFFIX
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleNameWarningValidator
import com.varabyte.kobweb.intellij.util.kobweb.style.createStyleNameErrorValidator
import com.varabyte.kobweb.intellij.util.psi.getEntireDotQualifiedExpression
import com.varabyte.kobweb.intellij.util.text.capitalized
import com.varabyte.kobweb.intellij.wizards.SimpleWizard
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance
import javax.swing.JComponent

/**
 * Extracted information about some target `Modifier.a(...).b(...).c(...)` chain.
 *
 * @param chainTerminator A final function on the chain which does not itself return a modifier. In other words, it ends
 *    the chain. We need to record it so we can make sure we put it back after our refactoring is complete.
 */
class ModifierChainInfo(val entries: List<Entry>, val chainTerminator: KtCallExpression?) {
    class Entry(
        val webModifier: KtNamedFunction,
        val webModifierType: WebModifierType,
        val parameters: List<Parameter>,
        val hasTrailingLambda: Boolean,
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
    val STYLE_NAME by key<String>()
    val MODIFIER_CHAIN_INFO by key<ModifierChainInfo>()
    val USE_CONCISE_SYNTAX by key<Boolean>()
    val EXTRACT_ATTRIBUTES by key<Boolean>()

    val REMEMBER_SYNTAX_CHOICE by key(false)
    val REMEMBER_EXTRACT_ATTRIBUTES_CHOICE by key(false)
    val SKIP_ATTRIBUTE_WARNING by key(false)
}

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
        var current: KtExpression? = this@toModifierChainInfo
        var chainTerminator: KtCallExpression? = null
        while (current is KtDotQualifiedExpression) {
            val callExpression = (current.selectorExpression as? KtCallExpression)
            val namedFun = callExpression
                ?.resolveToCall()
                ?.singleFunctionCallOrNull()
                ?.symbol?.psi
                    as? KtNamedFunction

            if (namedFun != null) {
                if (!namedFun.isModifierChainingExtension()) {
                    check(chainTerminator == null) { "There should only ever be at most one non-Modifier function in a Modifier chain (which, if present, terminates it!)"}
                    chainTerminator = callExpression
                } else {
                    chainedCalls.add(0, namedFun to callExpression)
                }
            }
            current = current.receiverExpression
        }

        val entries = chainedCalls.map { (funcDefn, callExpr) ->
            val funcCall = callExpr.resolveToCall()?.singleFunctionCallOrNull()
            val hasTrailingLambda = callExpr.valueArguments.lastOrNull() is KtLambdaArgument
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

                                var containerSymbol: KaSymbol? = refSymbol.containingSymbol

                                while (containerSymbol != null) {
                                    when (containerSymbol) {
                                        is KaPackageSymbol -> return@all true
                                        is KaNamedClassSymbol -> {
                                            // If we're a property declared inside a companion object, we can abort early
                                            if (containerSymbol.classKind == KaClassKind.COMPANION_OBJECT) return@all true
                                            if (containerSymbol.isLocal) return@all false
                                            // If we're a nested class, we must also verify parent containers
                                            containerSymbol = containerSymbol.containingSymbol
                                        }

                                        // Any local variables, local functions, or inner classes bounded to an instance/local context
                                        else -> return@all false
                                    }
                                }
                                true
                            }

                            ModifierChainInfo.Entry.Parameter.Value(
                                valueArgument.text,
                                isGlobal,
                            )
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
                parameters,
                hasTrailingLambda
            )
        }

        return ModifierChainInfo(entries, chainTerminator)
    }

    override fun createSteps(ctx: SimpleWizard<Input, Result>.StepContext): List<Step> {
        val appSettings = KobwebAppSettingsService.getInstance().state
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
            input.modifierChainStart.text
                .lines()
                .mapIndexed { i, line ->
                    val toDrop = if (i == 0) 0 else {
                        minOf(
                            // Don't crash if the line is all whitespace (e.g. an empty line between braces, perhaps)
                            line.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: line.length,
                            indent
                        )
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
                        @Composable
                        fun SomeWidget() {
                            val id = "my-id"
                            // The following example attribute
                            // modifiers can't be extracted due to
                            // assignments tied to the local scope.
                            MyStyle.toModifier()
                                .id(id)
                                //  ^^
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
                            "<html>This modifier chain includes at least one attribute modifier set to a value tied to the local scope, which means it cannot be extracted and will be left behind, inline.<br><br>This is probably fine!",
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
                override val headerText = "Preview"

                private val codeSummary = ctx.utils.components.kotlinCode()

                override fun produceComponent(): JComponent {
                    return ctx.utils.components.formBuilder()
                        .addComponent(JBLabel("<html>On pressing <b>Finish</b>, your code will be changed as follows:<html>"))
                        .addComponentFillVertically(codeSummary, 8)
                        .panel
                }

                override fun onEntering() {
                    val codeGen = ExtractCssCodeGenerator(ctx.data.toResult())
                    codeSummary.text = buildString {
                        appendLine("// CssStyle")
                        codeGen.cssStyleLines().forEach { line -> appendLine("+$line") }
                        appendLine()
                        appendLine("// Inline Modifier")
                        modifierDisplayText.split("\n").forEach { line -> appendLine("-$line") }
                        codeGen.inlineModifierLines().forEach { line -> appendLine("+$line") }
                    }
                }
            }
        )
    }

    override fun onFinished(data: Data): Result {
        val appSettings = KobwebAppSettingsService.getInstance().state
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
        val varNames = associatedStyleVariableNames()
        fun ModifierChainInfo.Entry.Parameter.toText(): String {
            return varNames[this]?.let { varName -> "${varName}.value()" } ?: value!!.text
        }

        append(webModifier.name!!)

        val nonDefaultParams = parameters.filter { varNames.containsKey(it) || it.value != null }
        val paramsToRender = nonDefaultParams.let {
            if (hasTrailingLambda) it.dropLast(1) else it
        }

        if (paramsToRender.isNotEmpty() || !hasTrailingLambda) {
            append('(')
            append(paramsToRender.joinToString(", ") { it.toText() })
            append(')')
        }
        if (hasTrailingLambda) {
            append(' ')
            val lastParam = nonDefaultParams.last()
            append(lastParam.toText()) // Includes lambda braces already
        }
    }

    private fun ExtractCssStyleWizard.Result.imports(): List<FqName> {
        val importsBuilder = mutableSetOf<String>()
        importsBuilder.add("com.varabyte.kobweb.silk.style.CssStyle")
        importsBuilder.add("com.varabyte.kobweb.silk.style.toModifier")
        if (useConciseSyntax) {
            importsBuilder.add("com.varabyte.kobweb.silk.style.base")
        }
        val styleVariables = modifierChainInfo.extractStyleVariables()
        if (styleVariables.isNotEmpty()) {
            importsBuilder.add("com.varabyte.kobweb.compose.css.StyleVariable")
            importsBuilder.add("com.varabyte.kobweb.compose.ui.modifiers.setVariable")
            styleVariables.forEach { (parameter, _) ->
                parameter.type.imports.forEach { fqn ->
                    importsBuilder.add(fqn)
                }
            }
        }
        return importsBuilder.sorted().map { FqName(it) }
    }

    fun importLines(skipImports: Set<FqName>): List<String> = with(result) {
        imports()
            .filter { it !in skipImports }
            .map { "import ${it.asString()}" }
    }

    fun cssStyleLines(): List<String> = with(result) {
        val attrModifiersToExtract =
            if (extractAttributes) {
                modifierChainInfo.entries.filter { it.webModifierType == WebModifierType.ATTRS && !it.hasParameterWithLocalValue() }
            } else emptyList()
        val styleModifiersToExtract = modifierChainInfo.entries.filter { it.webModifierType == WebModifierType.STYLE }

        fun createModifier(indent: String) = buildString {
            append("${indent}Modifier")
            val putModifiersOnNewLines = styleModifiersToExtract.size > 1
            styleModifiersToExtract
                .forEach { modifierEntry ->
                    if (putModifiersOnNewLines) {
                        appendLine()
                        append(indent)
                        append('\t')
                    }
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

        return buildString {
            modifierChainInfo.extractStyleVariables().forEach { (parameter, varName) ->
                appendLine("val $varName by StyleVariable<${parameter.type.rendered}>()")
            }
            append("val $styleName = CssStyle")
            if (useConciseSyntax) {
                append(".base")
                extraModifierParam?.let { append("($it)") }
                appendLine(" {")
                appendLine(createModifier(indent = "\t"))
                append("}")
            } else {
                extraModifierParam?.let { append("($it)") }
                appendLine(" {")
                appendLine("\tbase {")
                appendLine(createModifier(indent = "\t\t"))
                appendLine("\t}")
                append("}")
            }
        }.split("\n")
    }

    fun inlineModifierLines(): List<String> = with(result) {
        val inlineModifiers = run {
            val inlineAttrModifiers = modifierChainInfo.entries.filter { it.webModifierType == WebModifierType.ATTRS && (!extractAttributes || it.hasParameterWithLocalValue()) }
            modifierChainInfo.entries.filter { inlineAttrModifiers.contains(it) || it.webModifierType == WebModifierType.UNKNOWN }
        }

        val chainedCalls = buildList {
            inlineModifiers.forEach { modifierEntry -> add(modifierEntry.toText()) }
            modifierChainInfo.extractStyleVariables().forEach { (parameter, varName) ->
                add(buildString {
                    append("setVariable(")
                    append(varName)
                    append(", ")
                    append(parameter.value!!.text)
                    append(')')
                })
            }
            modifierChainInfo.chainTerminator?.let { chainTerminator -> add(chainTerminator.text) }
        }

        return buildString {
            append("$styleName.toModifier()")

            if (chainedCalls.size > 1) {
                appendLine()
                chainedCalls.forEach { call ->
                    append('\t')
                    append('.')
                    appendLine(call)
                }
            } else {
                chainedCalls.firstOrNull()?.let { call ->
                    append('.')
                    append(call)
                }
                appendLine()
            }
        }.split("\n")
            .also {
                // We always add a final empty newline at the end, but it shouldn't be returned as a line
                check(it.last().isEmpty())
            }
            .dropLast(1)
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

                val importStrs = codeGen.importLines(skipImports = existingImports).joinToString("\n")
                val dummyImports = psiFactory.createFile(importStrs).importList!!
                ktFile.importList?.apply {
                    dummyImports.imports.forEach { add(it) }
                } ?: run {
                    ktFile.add(dummyImports)
                }

                // Next, handle the Modifier -> CssStyle extraction
                val dummyFile = psiFactory.createFile(codeGen.cssStyleLines().joinToString("\n"))
                val anchor = topLevelDeclaration ?: ktFile.declarations.firstOrNull()

                for (declaration in dummyFile.declarations) {
                    if (anchor != null) {
                        ktFile.addBefore(declaration, anchor)
                        ktFile.addBefore(psiFactory.createNewLine(), anchor)
                    } else {
                        ktFile.add(declaration)
                    }
                }

                val replacementExpr = psiFactory.createExpression(codeGen.inlineModifierLines().joinToString("\n"))
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