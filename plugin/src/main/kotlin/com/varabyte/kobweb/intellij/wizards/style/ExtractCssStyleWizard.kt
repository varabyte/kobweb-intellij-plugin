package com.varabyte.kobweb.intellij.wizards.style

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
import com.intellij.psi.PsiWhiteSpace
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
import com.varabyte.kobweb.intellij.util.kobweb.compose.COMPOSABLE_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.compose.MUTABLE_STATE_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierChainingExtension
import com.varabyte.kobweb.intellij.util.kobweb.silk.COLOR_MODE_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.silk.COLOR_MODE_COMPANION_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.style.CSS_STYLE_SUFFIX
import com.varabyte.kobweb.intellij.util.kobweb.style.StyleNameWarningValidator
import com.varabyte.kobweb.intellij.util.kobweb.style.createStyleNameErrorValidator
import com.varabyte.kobweb.intellij.util.psi.AttachedComments
import com.varabyte.kobweb.intellij.util.psi.findAttachedComments
import com.varabyte.kobweb.intellij.util.psi.getEntireDotQualifiedExpression
import com.varabyte.kobweb.intellij.util.psi.resolvedClassId
import com.varabyte.kobweb.intellij.util.text.capitalized
import com.varabyte.kobweb.intellij.util.text.replaceAll
import com.varabyte.kobweb.intellij.wizards.SimpleWizard
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.psi.childrenDfsSequence
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
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
        val attachedComments: AttachedComments,
    ) {
        class Parameter(
            val name: String,
            val type: Type,
            val value: Value?,
            val isVararg: Boolean, // If true, `value.text` will look like `"10, 20, 30"`
        ) {
            class Value(
                val isNamed: Boolean,
                val text: String,
                val isLocallyBound: Boolean,
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
             * If true, it means this parameter is bound to a variable or method tied to the local scope.
             *
             * For example, this means if we refactor out a style modifier whose value is tied to a local variable, we
             * will need to create an accompanying StyleVariable with it, as a way to pass that value from the local
             * scope over to the CssStyle.
             */
            fun isLocallyBound() = value != null && value.isLocallyBound
        }

        /**
         * Test if this entry has at least one parameter that is tied to local scope.
         */
        fun hasLocallyBoundParameter(): Boolean {
            return parameters.any { it.isLocallyBound() }
        }

        /**
         * Test if this entry has at least one *vararg* parameter that is tied to local scope.
         */
        fun hasLocallyBoundVarargParameter(): Boolean {
            return parameters.any { it.isLocallyBound() && it.isVararg }
        }
    }
}

context(kaSession: KaSession)
fun KtDotQualifiedExpression.toModifierChainInfo(): ModifierChainInfo = with(kaSession) {
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
                // Usually, a parameter has a single value, but if it's a vararg, it may have multiple values.
                val argValueExprs = argMapping.entries
                    .filter { it.value.symbol == paramSymbol }
                    .map { it.key }

                val paramValue = if (argValueExprs.isEmpty()) null else {
                    fun KtExpression.getReferencedSimpleNames(): List<KtSimpleNameExpression> {
                        return PsiTreeUtil.findChildrenOfAnyType(this, /*strict = */false, KtSimpleNameExpression::class.java).toList()
                    }

                    context(kaSession: KaSession)
                    fun KtSimpleNameExpression.isColorModeReference(): Boolean = with(kaSession) {
                        val symbol = mainReference.resolveToSymbol() ?: return false
                        val self = this@isColorModeReference

                        // Check direct property reference to ColorMode.current, ColorMode.currentState, or
                        // ColorMode.currentState.value.
                        if (symbol is KaPropertySymbol) {
                            (symbol.containingSymbol as? KaNamedClassSymbol)?.let { containingSymbol ->
                                if (
                                    containingSymbol.classId == COLOR_MODE_COMPANION_CLASS_ID &&
                                    symbol.name.asString().let { it == "currentState" || it == "current" }
                                ) {
                                    return true
                                } else if (containingSymbol.classId == MUTABLE_STATE_CLASS_ID && symbol.name.asString() == "value") {
                                    // If here, check specifically only for "ColorMode.currentState.value"; we don't
                                    // want to accidentally replace something that shouldn't be substituted
                                    val colorModeCurrentStateExpr = (self.parent as? KtDotQualifiedExpression)
                                        // Verify we are looking at a dot qualified expr like `currentState.value`, not
                                        // `value.isDark`
                                        ?.takeIf { it.selectorExpression == self }
                                        // should be `ColorMode.currentState`
                                        ?.receiverExpression as? KtDotQualifiedExpression
                                        ?: return false
                                    // `ColorMode.currentState` -> `currentState`
                                    val currentStateExpr = (colorModeCurrentStateExpr.selectorExpression) as? KtSimpleNameExpression ?: return false
                                    if (currentStateExpr.getReferencedName() != "currentState") return false
                                    // `ColorMode.currentState` -> `ColorMode`
                                    val colorModeExpr = colorModeCurrentStateExpr.receiverExpression
                                    return colorModeExpr.resolvedClassId == COLOR_MODE_COMPANION_CLASS_ID
                                }
                            }
                        }

                        // Check variable assigned to / delegate property bound to ColorMode.current / by ColorMode.currentState
                        if (symbol is KaLocalVariableSymbol && symbol.returnType.expandedSymbol?.classId == COLOR_MODE_CLASS_ID) {
                            val property = symbol.psi as? KtProperty ?: return false

                            // A plain `var` could be reassigned to some other value later (while
                            // `var by ColorMode.currentState` is bound to a color mode reference for its lifetime.)
                            if (property.isVar && property.delegateExpression == null) return false

                            // The whole expression must *be* a color mode reference, not just contain one somewhere. In other words,
                            // `val c = ColorMode.current` but not `val c = if (cond) ColorMode.current else ColorMode.LIGHT.
                            val rhsExpr = (property.delegateExpression ?: property.initializer)
                                ?.let { KtPsiUtil.safeDeparenthesize(it) }
                                ?: return false

                            // `ColorMode.current` -> `current`, `ColorMode.currentState.value` -> `value`, `current` -> `current`
                            val lastName =
                                (((rhsExpr as? KtDotQualifiedExpression)?.selectorExpression ?: rhsExpr) as? KtSimpleNameExpression)
                                    ?: return false
                            return lastName.isColorModeReference()
                        }
                        return false
                    }

                    /**
                     * Find all inner expressions that reference the ColorMode class.
                     *
                     * This is useful because we can intercep these references and shortcut them to the
                     * `CssStyle.colorMode` property.
                     */
                    context(_: KaSession)
                    fun KtExpression.findColorModeReferences(): List<KtSimpleNameExpression> {
                        return childrenDfsSequence().filterIsInstance<KtSimpleNameExpression>().filter { it.isColorModeReference() }.toList()
                    }

                    // Check if all references in an argument are global. If all method arguments are global, we will be
                    // able to move the function call to a top-level CssStyle trivially.
                    fun List<KtSimpleNameExpression>.areAllGlobal(excludedReferences: Set<KtSimpleNameExpression>): Boolean = this.all { ref ->
                        if (excludedReferences.contains(ref)) return@all true

                        // "this" and "super" always refer to a local scope and should never be extracted to a CssStyle,
                        // where `this` would mean something else and `super` would be nonsense in that context.
                        if (ref.parent is KtThisExpression || ref.parent is KtSuperExpression) return@all false

                        // Literals or unresolved names (e.g. '100', 'true') have no symbol
                        val refSymbol = ref.mainReference.resolveToSymbol() ?: return@all true
                        if (refSymbol is KaDeclarationSymbol && refSymbol.isTopLevel) return@all true

                        // Figure out if this reference is the `member` part of a `receiver.member` expression. For
                        // properties, the reference itself sits after the dot (`receiver.member`), but for method
                        // calls, what sits after the dot is the whole call, not just its name (`receiver.member()` is
                        // `receiver` + `member()`), so step up to the call first.
                        val selector = (ref.parent as? KtCallExpression)?.takeIf { it.calleeExpression == ref } ?: ref
                        val qualifiedReceiver = selector.getQualifiedExpressionForSelector()?.receiverExpression
                        // For `receiver.member`, `member` is only as global as `receiver` is. Every name inside
                        // `receiver` is also in this list and is checked on its own, so if any of them is local, the
                        // overall check already failed there.
                        if (qualifiedReceiver != null) return@all true

                        var containerSymbol: KaSymbol? = refSymbol.containingSymbol

                        while (containerSymbol != null) {
                            when (containerSymbol) {
                                is KaPackageSymbol -> return@all true
                                is KaNamedClassSymbol -> {
                                    // Companion objects are always global, so we can abort early
                                    if (containerSymbol.classKind == KaClassKind.COMPANION_OBJECT) return@all true
                                    // Enums too
                                    if (containerSymbol.classKind == KaClassKind.ENUM_CLASS) return@all true
                                    // Objects are global only if they are top level or nested in outer objects
                                    if (containerSymbol.classKind == KaClassKind.OBJECT && containerSymbol.isTopLevel) return@all true

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

                    // CssStyle blocks are not themselves composable, so we cannot port over anything that requires
                    // a composable context to call it.
                    // The kinds of expressions that arguments might contain that are composable are...
                    // 1) property getters, e.g. @get:Composable val MODIFIER_VALUE = ...
                    // 2) function calls, e.g. produceColor() where `@Composable fun produceColor() ...``
                    context(kaSession: KaSession)
                    fun KtValueArgument.includesComposableReference(skip: Set<KtExpression>): Boolean {
                        val self = this.getArgumentExpression() ?: return false
                        with(kaSession) {
                            fun KaAnnotated.hasComposableAnnotation(): Boolean {
                                return annotations.contains(COMPOSABLE_CLASS_ID)
                            }

                            fun <T : KtExpression> Iterable<T>.removeSkipped(): Iterable<T> =
                                if (skip.isEmpty()) this else this.filter { !skip.contains(it) }

                            val calls = PsiTreeUtil.findChildrenOfAnyType(
                                self, /* strict = */ false, KtCallExpression::class.java
                            )
                            for (call in calls.removeSkipped()) {
                                val functionSymbol = call.resolveToCall()
                                    ?.singleFunctionCallOrNull()
                                    ?.symbol as? KaNamedFunctionSymbol ?: continue

                                if (functionSymbol.hasComposableAnnotation()) {
                                    return true
                                }
                            }

                            val nameExprs = PsiTreeUtil.findChildrenOfAnyType(
                                self, /* strict = */ false, KtSimpleNameExpression::class.java
                            )
                            for (nameExpr in nameExprs.removeSkipped()) {
                                val symbol = nameExpr.mainReference.resolveToSymbol() ?: continue
                                if (symbol is KaPropertySymbol && symbol.getter?.hasComposableAnnotation() == true)
                                    return true
                            }
                        }

                        return false
                    }

                    // e.g. "current" -> "ColorMode.current"
                    fun Set<KtSimpleNameExpression>.toQualifiedNames(): Set<String> =
                        this.map { nameExpr ->
                            var topQualifiedExpr: KtExpression = nameExpr
                            while (topQualifiedExpr.parent is KtDotQualifiedExpression &&
                                (topQualifiedExpr.parent as KtDotQualifiedExpression).selectorExpression == topQualifiedExpr) {
                                topQualifiedExpr = topQualifiedExpr.parent as KtDotQualifiedExpression
                            }

                            topQualifiedExpr.text
                        }.toSet()

                    class ArgResult(val text: String, val isLocallyBound: Boolean)

                    // A KtValueArgument includes the full expression, e.g. not just "10" but "value = 10" if the user
                    // included the parameter name explicitly
                    val valueArguments = argValueExprs.map { it.findParentOfType<KtValueArgument>()!! }

                    val argResults = argValueExprs.zip(valueArguments).map { (argValueExpr, valueArgument) ->
                        val references = argValueExpr.getReferencedSimpleNames()
                        val colorModeReferences = argValueExpr.findColorModeReferences().toSet()
                        // colorModeReferences that we find would normally be bound to local scope -- but we plan
                        // to intercept and rewrite them using the provided CssStyle.colorMode instead, thereby
                        // cutting the ties to the local scope.
                        val isGlobal = references.areAllGlobal(colorModeReferences)

                        // The argument expression excludes the spread operator (e.g. `arr` in `*arr`), so we need
                        // to add it back in ourselves.
                        val spread = if (valueArgument.getSpreadElement() != null) "*" else ""

                        ArgResult(
                            text = spread + argValueExpr.text.replaceAll(colorModeReferences.toQualifiedNames(), "colorMode"),
                            isLocallyBound = !isGlobal || valueArgument.includesComposableReference(colorModeReferences),
                        )
                    }

                    ModifierChainInfo.Entry.Parameter.Value(
                        // A named vararg can only ever be passed a single (spread) argument, so checking the first
                        // argument is sufficient
                        valueArguments.first().isNamed(),
                        argResults.joinToString(", ") { it.text },
                        argResults.any { it.isLocallyBound },
                    )
                }

                @OptIn(KaExperimentalApi::class)
                val paramType = if (paramSymbol.returnType.symbol != null) {
                    // Even if the parameter itself is nullable, we just want the underlying non-nulllable type, since
                    // we may want to import it, use its type with StyleVariable, etc.
                    val paramSymbolReturnType = paramSymbol.returnType.withNullability(isMarkedNullable = false)

                    // There HAS to be a better way than this, but I fought the IntelliJ APIs and could not find
                    // a way that worked with generic types, regular types, AND type-alias values. So what I do for
                    // now is "parse" the fqns out of the qualified-name version of the render.
                    val qualifiedRender = paramSymbolReturnType.render(
                        KaTypeRendererForSource.WITH_QUALIFIED_NAMES,
                        position = Variance.INVARIANT
                    )

                    ModifierChainInfo.Entry.Parameter.Type(
                        qualifiedRender.split(Regex("[<>, ]")).filter { it.isNotBlank() && it.trim() !in setOf("*", "in", "out") }.toSet(),
                        paramSymbolReturnType.render(
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
                    paramValue,
                    paramSymbol.isVararg,
                )
            })
        }
        ModifierChainInfo.Entry(
            funcDefn,
            funcDefn.getWebModifierType(),
            parameters,
            hasTrailingLambda,
            callExpr.findAttachedComments()
        )
    }

    return ModifierChainInfo(entries, chainTerminator)
}


private object Keys {
    val STYLE_NAME by key<String>()
    val MODIFIER_CHAIN_INFO by key<ModifierChainInfo>()
    val USE_CONCISE_SYNTAX by key<Boolean>()
    val EXTRACT_ATTRIBUTES by key<Boolean>()

    val REMEMBER_SYNTAX_CHOICE by key(false)
    val REMEMBER_EXTRACT_ATTRIBUTES_CHOICE by key(false)
    val SKIP_STYLE_VARIABLE_INFO by key(false)
    val SKIP_STYLE_WARNING by key(false)
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
        val initialPrefix: String,
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
            // so we can't just use trimIndent but need special logic to drop the indent on the latter lines.
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

                    JBTextField(initialName).apply {
                        this.selectionStart = 0
                        this.selectionEnd = this.text.length - CSS_STYLE_SUFFIX.length
                    }
                }

                override fun produceComponent(): JComponent {
                    return ctx.utils.components.formBuilder()
                        .addComponent(JBLabel("Target for extraction:"))
                        .addComponentFillVertically(ctx.utils.components.kotlinCode(modifierDisplayText), 8)
                        .addComponent(JBLabel("<html>A good style name is one that is descriptive of what the style is for, while being as short as reasonably possible.</html>"))
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
                            "<html>The <b>concise</b> format is generally recommended as it reduces indentation, unless you plan to add <a href=\"https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Selectors\">additional selectors</a> soon (e.g., <code>hover</code>, <code>focus</code>, <code>link</code>).",
                        ))
                        .addComponent(JBLabel("<html><i>Do not stress too much about the decision, as you can easily refactor this later.</i></html>"))
                        .addComponent(rememberCheckbox)
                        .panel
                }

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
                override val headerText = "Locally Bound Style Modifier(s)"

                private val doNotShowCheckbox = ctx.utils.components.doNotShowAgainCheckbox()

                override fun shouldShow(): Boolean {
                    if (!appSettings.extractCssStyle.showStyleVariableInfo) return false

                    val modifierChainInfo = ctx.data.getUserData(Keys.MODIFIER_CHAIN_INFO) ?: return false

                    return (modifierChainInfo.entries.any { it.webModifierType == WebModifierType.STYLE && it.hasLocallyBoundParameter() })
                }

                override fun produceComponent(): JComponent {
                    return ctx.utils.components.formBuilder()
                        .addComponent(JBLabel(
                            "<html>This modifier chain includes at least one style modifier set to a value tied to the local scope. This means we will introduce a <code>StyleVariable</code> as a way to communicate between the call site and the extracted style itself.<br><br>For example:</html>",
                        ))
                        .addComponentFillVertically(
                            ctx.utils.components.kotlinCode(
                                """
                                    // Before
                                    @Composable
                                    fun SomeWidget() {
                                        val radius = 5.px
                                        Modifier.borderRadius(radius)
                                        //                    ^^^^^^
                                        // bound to "radius" from local scope
                                    }

                                    // After
                                    val MyStyle_BorderRadiusVar by StyleVariable<CSSLengthOrPercentageNumericValue>()
                                    //  ^^^^^^^^^^^^^^^^^^^^^^^
                                    val MyStyle = CssStyle.base {
                                        Modifier.borderRadius(MyStyle_BorderRadiusVar.value())
                                        //                    ^^^^^^^^^^^^^^^^^^^^^^^
                                    }
                                    
                                    @Composable
                                    fun SomeWidget() {
                                        val radius = 5.px
                                        MyStyle.toModifier().setVariable(MyStyle_BorderRadiusVar, radius)
                                        //                               ^^^^^^^^^^^^^^^^^^^^^^^
                                    }
                                """.trimIndent()
                            ), 8)
                        .addComponent(JBLabel(
                            "<html>This is not a problem! But you can cancel the extraction of this style and inline the parameter first before trying this again if you'd like. Or you can manually copy the variable over to the newly extracted <code>CssStyle</code> afterwards and delete the <code>StyleVariable</code> by hand.</html>",
                        ))
                        .addComponent(doNotShowCheckbox)
                        .panel
                }

                override fun onNext() {
                    ctx.data.putUserData(Keys.SKIP_STYLE_VARIABLE_INFO, doNotShowCheckbox.isSelected)
                }
            },
            object : Step {
                override val headerText = "Non-Extractable Style Modifier(s)"

                private val doNotShowCheckbox = ctx.utils.components.doNotShowAgainCheckbox()

                override fun shouldShow(): Boolean {
                    if (!appSettings.extractCssStyle.showStyleWarning) return false

                    val modifierChainInfo = ctx.data.getUserData(Keys.MODIFIER_CHAIN_INFO) ?: return false

                    return (modifierChainInfo.entries.any { entry ->
                        entry.webModifierType == WebModifierType.STYLE && entry.hasLocallyBoundVarargParameter()
                    })
                }

                override fun produceComponent(): JComponent {
                    return ctx.utils.components.formBuilder()
                        .addComponent(JBLabel(
                            "<html>Locally bound style values are usually passed to the extracted style using a <code>StyleVariable</code>, but this modifier chain includes at least one style modifier whose value can't be wrapped that way. It will be left behind, inline.<br><br>For example:</html>"
                        ))
                        .addComponentFillVertically(
                            ctx.utils.components.kotlinCode(
                                """
                                    @Composable
                                    fun SomeWidget() {
                                        val fonts = listOf("Roboto", "sans-serif")
                                        MyStyle.toModifier()
                                            .fontFamily(*fonts.toTypedArray())
                                            //          ^^^^^^^^^^^^^^^^^^^^^
                                            // A StyleVariable can't hold a
                                            // variable number of values!
                                    }
                                """.trimIndent()
                            ), 8)
                        .addComponent(doNotShowCheckbox)
                        .panel
                }

                override fun onNext() {
                    ctx.data.putUserData(Keys.SKIP_STYLE_WARNING, doNotShowCheckbox.isSelected)
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
                            "<html>This modifier chain includes at least one attribute modifier. Would you like to keep attributes declared inline at this call site, or would you like to extract them as well, attaching them to the CssStyle via its <code>extraModifier</code> parameter?</html>"
                        ))
                        .addComponent(JBLabel("<html><i>You can <b>Leave Inline</b> if you're not sure.</i></html>"))
                        .addComponentFillVertically(choices.component, 8)
                        .addComponent(rememberCheckbox)
                        .panel
                }

                override fun onNext() {
                    ctx.data.putUserData(Keys.EXTRACT_ATTRIBUTES, choices.targetInfo == extractChoice)
                    ctx.data.putUserData(Keys.REMEMBER_EXTRACT_ATTRIBUTES_CHOICE, rememberCheckbox.isSelected)
                }
            },
            object : Step {
                override val headerText = "Locally Bound Attribute Modifier(s)"

                private val codeExample = ctx.utils.components.kotlinCode(
                    """
                        @Composable
                        fun SomeWidget() {
                            val id = "my-id"
                            // The following example attribute
                            // modifiers can't be extracted due to
                            // arguments tied to the local scope.
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

                    return (extractAttrModifiers && modifierChainInfo.entries.any { it.webModifierType == WebModifierType.ATTRS && it.hasLocallyBoundParameter() })
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
        if (data.getUserData(Keys.SKIP_STYLE_VARIABLE_INFO)!!) {
            appSettings.extractCssStyle.showStyleVariableInfo = false
        }
        if (data.getUserData(Keys.SKIP_STYLE_WARNING)!!) {
            appSettings.extractCssStyle.showStyleWarning = false
        }
        if (data.getUserData(Keys.SKIP_ATTRIBUTE_WARNING)!!) {
            appSettings.extractCssStyle.showAttributeWarning = false
        }

        return result
    }
}

private fun List<ModifierChainInfo.Entry>.targetedForExtraction(extractAttributes: Boolean): List<ModifierChainInfo.Entry> {
    return this.filter {
        when (it.webModifierType) {
            WebModifierType.ATTRS -> extractAttributes && !it.hasLocallyBoundParameter()
            WebModifierType.STYLE -> !it.hasLocallyBoundVarargParameter()
            else -> false
        }
    }
}

private class ExtractCssCodeGenerator(val result: ExtractCssStyleWizard.Result) {
    // The full names of StyleVariable names that will be associated with parameters that need them (i.e., because
    // they are bound to a local value from the original scope). If no entry is found for the target parameter,
    // there is no declared style variable for it, and it should just copy its contents over as is.
    private fun ModifierChainInfo.Entry.associatedStyleVariableNames(): Map<ModifierChainInfo.Entry.Parameter, String> {
        if (this.webModifierType != WebModifierType.STYLE || this.hasLocallyBoundVarargParameter()) return emptyMap()

        // e.g. `Modifier.backgroundColor(colorVar)` + `val MyStyle = CssStyle { ... }`
        //      --> MyStyle_BackgroundColorVar
        val multiParameterModifier = parameters.size > 1
        return parameters.filter { it.isLocallyBound() }.associateWith { param ->
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
            self.entries.targetedForExtraction(result.extractAttributes)
                .filter { it.webModifierType == WebModifierType.STYLE }
                .forEach { entry ->
                    this.putAll(entry.associatedStyleVariableNames())
                }
        }
    }

    private fun ModifierChainInfo.Entry.toText() = buildString {
        val varNames = associatedStyleVariableNames()
        fun ModifierChainInfo.Entry.Parameter.toText(): String {
            val param = this
            // NOTE: We only call this for parameters that have their values set, i.e., not defaults
            val paramValue = value!!

            return buildString {
                if (paramValue.isNamed) {
                    append("$name = ")
                }
                append(varNames[param]?.let { varName -> "${varName}.value()" } ?: paramValue.text)
            }
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
        val webModifiersToExtract = modifierChainInfo.entries.targetedForExtraction(extractAttributes)
        val (attrModifiersToExtract, styleModifiersToExtract) = webModifiersToExtract.partition { it.webModifierType == WebModifierType.ATTRS }

        fun createModifier(indent: String) = buildString {
            append("${indent}Modifier")
            val putModifiersOnNewLines =
                styleModifiersToExtract.size > 1 || (styleModifiersToExtract.size == 1 && styleModifiersToExtract[0].attachedComments.overhead.isNotEmpty())
            styleModifiersToExtract
                .forEach { modifierEntry ->
                    if (putModifiersOnNewLines) {
                        appendLine()
                        append(indent)
                        append('\t')
                    }

                    modifierEntry.attachedComments.overhead.forEach {
                        appendLine(it.text)
                        append(indent)
                        append('\t')
                    }
                    append('.')
                    append(modifierEntry.toText())
                    modifierEntry.attachedComments.trailing?.let { append(' '); append(it.text) }
                }
        }

        val extraModifierParam = if (attrModifiersToExtract.isNotEmpty()) {
            buildString {
                val useMultiLineFormat = attrModifiersToExtract.any { it.attachedComments.hasAny() }
                if (!useMultiLineFormat) {
                    append("extraModifier = { Modifier")
                    attrModifiersToExtract.forEach { modifierEntry ->
                        append('.')
                        append(modifierEntry.toText())
                    }
                    append(" }")
                } else {
                    appendLine("extraModifier = {")
                    appendLine("\tModifier")
                    attrModifiersToExtract.forEach { modifierEntry ->
                        modifierEntry.attachedComments.overhead.forEach { overheadComment ->
                            append("\t\t")
                            appendLine(overheadComment.text)
                        }
                        append("\t\t")
                        append('.')
                        append(modifierEntry.toText())
                        modifierEntry.attachedComments.trailing?.let { append(' '); append(it.text) }
                        appendLine()
                    }
                    append("}") // Do NOT appendLine -- this allows the codegen to add a trailing ")" after the brace
                }
            }
        } else null

        return buildString {
            modifierChainInfo.extractStyleVariables().forEach { (parameter, varName) ->
                appendLine("private val $varName by StyleVariable<${parameter.type.rendered}>()")
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
            val extractedModifiers = modifierChainInfo.entries.targetedForExtraction(extractAttributes).toSet()
            modifierChainInfo.entries.filter { !extractedModifiers.contains(it) }
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
                val origInlineModifierExpr = modifierChainStart.getEntireDotQualifiedExpression()

                // If there is a trailing comment after the end of the inline modifier which we will move over to
                // the CssStyle block, delete it from here so it won't be duplicated in two places
                val trailingComment = origInlineModifierExpr.findAttachedComments().trailing
                    ?.takeIf { psiComment ->
                        psiComment == modifierChainInfo.entries.targetedForExtraction(extractAttributes)
                            .lastOrNull()?.attachedComments?.trailing
                    }

                SmartPointerManager.getInstance(project).createSmartPsiElementPointer(
                    origInlineModifierExpr.replace(replacementExpr).also {
                        if (trailingComment != null) {
                            (trailingComment.prevSibling as? PsiWhiteSpace)?.delete()
                            trailingComment.delete()
                        }
                    }
                )
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