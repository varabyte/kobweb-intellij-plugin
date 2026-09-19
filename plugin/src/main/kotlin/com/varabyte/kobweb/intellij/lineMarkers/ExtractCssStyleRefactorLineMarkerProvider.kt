package com.varabyte.kobweb.intellij.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.CommonDataKeys
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
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.findParentOfType
import com.varabyte.kobweb.intellij.util.compose.STYLE_PROPERTY_VALUE_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.modifier.MODIFIER_CLASS_ID
import com.varabyte.kobweb.intellij.util.kobweb.modifier.WebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.getWebModifierType
import com.varabyte.kobweb.intellij.util.kobweb.modifier.isModifierCompanion
import com.varabyte.kobweb.intellij.util.kobweb.project.KobwebLineMarkerInfo
import com.varabyte.kobweb.intellij.util.kobweb.style.createStyleNameValidator
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonCallableId
import com.varabyte.kobweb.intellij.util.kobweb.style.styleSingletonClassId
import com.varabyte.kobweb.intellij.util.text.truncate
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

// TODO: Create a settings page as a way to reconfigure options chosen here

private const val ACTION_TITLE = "Extract Inline Modifier to CssStyle"

class ExtractCssStyleRefactorLineMarkerProvider : LineMarkerProvider {
    private class ModifierChainInfo(val entries: List<Entry>) {
        class Entry(
            val webModifier: KtNamedFunction,
            val webModifierType: WebModifierType,
            val parameters: List<BoundModifierParameter>,
        ) {
            fun hasParameterWithLocalValue(): Boolean {
                return parameters.any { it.hasLocalValue() }
            }
        }
    }
    private class BoundModifierParameter(
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

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is LeafPsiElement) return null // The docs for this class say it should ideally point at leaf elements
        val parent = element.parent ?: return null
        val namedExpression = parent as? KtNameReferenceExpression ?: return null
        // Early quick check abort to avoid potentially unnecessary analyze
        if (namedExpression.text != MODIFIER_CLASS_ID.shortClassName.identifier) return null

        // Get the element as the first item of a modifier chain
        val modifierChainStart = PsiTreeUtil.getParentOfType(parent, KtDotQualifiedExpression::class.java) ?: return null
        // Should never happen but think of this like an assertion that we ARE the first in the chain
        if (modifierChainStart.receiverExpression != parent) return null

        analyze(namedExpression) {
            if (!namedExpression.isModifierCompanion()) return null
        }

        if (isInsideExcludedContext(modifierChainStart)) return null

        return KobwebLineMarkerInfo(
            element,
            ACTION_TITLE,
            navHandler = { e, _ ->
                val dataContext = ActionToolbar.getDataContextFor(e.component)
                val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return@KobwebLineMarkerInfo

                handleExtractCssStyle(editor, modifierChainStart)
            },
        )
    }

    private fun isInsideExcludedContext(element: PsiElement): Boolean {
        var curr: PsiElement? = element.parent
        while (curr != null && curr !is KtFile) {
            if (curr is KtCallExpression) {
                analyze(curr) {
                    if (curr.styleSingletonCallableId != null) return true
                }
            } else if (curr is KtClass) {
                analyze(curr) {
                    if (curr.expressionType?.styleSingletonClassId != null) return true
                }
            }
            curr = curr.parent
        }
        return false
    }

    private fun handleExtractCssStyle(editor: Editor, modifierChainStart: KtDotQualifiedExpression) {
        val project = modifierChainStart.project
        val styleSuffix = "Style"
        val styleNameValidator = modifierChainStart.containingKtFile.createStyleNameValidator()
        val initialName = run {
            var attempt = 1
            var name: String
            do {
                name = "My${if (attempt == 1) "" else attempt.toString()}$styleSuffix"
                attempt++
            } while (!styleNameValidator.checkInput(name))
            name
        }

        val modifierDisplayText = modifierChainStart.text.replace(Regex("\n *"), "")
        val styleName = Messages.showInputDialog(
            project,
            "<html>Target for extraction:<pre>${modifierDisplayText.truncate(32)}</pre>Enter a name for the new CssStyle:</html>",
            ACTION_TITLE,
            Messages.getQuestionIcon(),
            initialName,
            styleNameValidator,
            TextRange(0, initialName.indexOf(styleSuffix))
        ) ?: return

        // We're almost ready to extract, but it's possible that several modifier functions take local variables as
        // parameters. We will use StyleVariables as a way to support modifiers whose values are set to local variables.
        //
        // For example:
        // ```
        // // BEFORE
        // @Composabile
        // fun SomeComposable {
        //   val myColor = Colors.Green
        //   Modifier.backgroundColor(myColor)
        // }
        //
        // // AFTER
        // private val WidgetStyleBackgroundColorVar by StyleVariable<CssColorValue>()
        // val WidgetStyle = CssStyle {
        //    base { Modifier.backgroundColor(WidgetStyleBackgroundColorVar.value()) }
        // }
        // @Composabile
        // fun SomeComposable {
        //   val myColor = Colors.Green
        //   WidgetStyle.toModifier()
        //     .setVariable(WidgetStyleBackgroundColorVar, myColor)
        // }
        // ```

        // We are currently on the EDT, which we are not allowed to block for too long, so process in a safer way
        val modifierChainInfo = runWithModalProgressBlocking(project, "Processing target Modifier...") {
            readAction { analyze(modifierChainStart) {
                modifierChainStart.toModifierChainInfo()
            } }
        }

        val attrModifiers = modifierChainInfo.entries.filter { it.webModifierType == WebModifierType.ATTRS }
        val extractAttributes = if (attrModifiers.isNotEmpty() && attrModifiers.any { !it.hasParameterWithLocalValue() }) {
            val result = Messages.showOkCancelDialog(
                project,
                "This modifier chain includes at least one attribute modifier. Would you like to keep attributes declared inline at this call site, or would you like to extract them as well, associating them alongside the CssStyle?\n\n(You can leave it inline if you're not sure.)",
                "Extract Attribute Modifier(s)?",
                "Leave Inline",
                "Extract to CssStyle",
                Messages.getQuestionIcon(),
            )
            result == Messages.CANCEL
        } else false

        val useConciseSyntax = run {
            val codeSnippet =
                """
                    // Concise
                    CssStyle.base { Modifier... }
                    
                    // Relaxed
                    CssStyle {
                        base { Modifier... }
                    }
                """.trimIndent()

            Messages.showOkCancelDialog(
                project,
                "<html>Would you like to use a concise syntax?<pre>$codeSnippet</pre>Concise is generally recommended, unless you plan to add <a href=\"https://developer.mozilla.org/en-US/docs/Web/CSS/Guides/Selectors\">additional selectors</a> soon (e.g., <code>hover</code>, <code>focus</code>, <code>link</code>).<br><br>(Do not stress too much about the decision, as you can easily refactor this later.)",
                "Use Concise Syntax?",
                "Concise",
                "Relaxed",
                Messages.getQuestionIcon(),
            ) == Messages.OK
        }

        if ((extractAttributes && attrModifiers.any { it.hasParameterWithLocalValue() }) || (attrModifiers.isNotEmpty() && attrModifiers.all { it.hasParameterWithLocalValue()})) {
            val codeSnippet =
                """
                    // Before
                    @Composable
                    fun SomeWidget() {
                        val id = "my-id"
                        MyStyle.toModifier().id(id)
                                             ^^^^^^
                    }
                    
                    // After
                    val MyStyle = CssStyle(
                        extraModifier = { Modifier.id("my-id") }
                        ^^^^^^^^^^^^^ 
                    ) {
                        ...
                    }

                    @Composable
                    fun SomeWidget() {
                        MyStyle.toModifier()
                    }
                """.trimIndent()
            Messages.showDialog(
                project,
                "<html>This modifier chain includes at least one attribute modifier set to a local variable or method result, which means it cannot be extracted and will be left here, inline, at the call site.<br><br>You can always manually move the attribute later, using <code>extraModifier</code>, like so:<pre>$codeSnippet</pre></html>",
                "Local Attribute Modifier(s)",
                arrayOf("OK"),
                /* defaultOptionIndex = */ 0,
                Messages.getInformationIcon(),
            )
            // TODO: Don't show this message again
        }

        // TODO: Do checkbox widgets ourselves because of "Never ask again" issue?
        performRefactoring(editor, modifierChainStart, styleName, modifierChainInfo, extractAttributes, useConciseSyntax)
    }

    private fun performRefactoring(
        editor: Editor,
        modifierChainStart: KtDotQualifiedExpression,
        styleName: String,
        modifierChainInfo: ModifierChainInfo,
        extractAttributes: Boolean,
        useConciseSyntax: Boolean
    ) {
        val ktFile = modifierChainStart.containingKtFile
        val project = modifierChainStart.project
        val psiFactory = KtPsiFactory(project)

        val (attrModifiersToExtract, attrModifiersToLeaveInPlace) = modifierChainInfo.entries
            .filter { it.webModifierType == WebModifierType.ATTRS }
            .partition { extractAttributes && !it.hasParameterWithLocalValue() }

        // For now, we also include unknown web modifier types, because as long as a modifier isn't mutable attributes,
        // it should be safe to put into a CssStyle. We reserve the right to change this behavior in the future, at
        // which point this would become `it.webModifierType == STYLE`
        val webModifiersToExtract = modifierChainInfo.entries.filter { it.webModifierType != WebModifierType.ATTRS }

        // The full names of StyleVariable names that will be associated with parameters that need them (i.e., because
        // they are bound to a local value from the original scope). If no entry is found for the target parameter,
        // there is no declared style variable for it, and it should just copy its contents over as is.
        fun ModifierChainInfo.Entry.associatedStyleVariableNames(): Map<BoundModifierParameter, String> {
            // e.g. `Modifier.backgroundColor(colorVar)` + `val MyStyle = CssStyle { ... }`
            //      --> MyStyle_BackgroundColorVar
            val multiParameterModifier = parameters.size > 1
            return parameters.filter { it.value != null && !it.value.isGlobal }.associateWith { param ->
                buildString {
                    append(styleName)
                    append('_')
                    append(webModifier.name!!.capitalized())
                    if (multiParameterModifier) {
                        append(param.name.capitalized())
                    }
                    append("Var")
                }
            }
        }

        fun ModifierChainInfo.Entry.toText() = buildString {
            append(webModifier.name!!)
            append('(')
            val varNames = associatedStyleVariableNames()

            append(parameters.filter { varNames.containsKey(it) || it.value != null }.joinToString(", ") { p ->
                varNames[p]?.let { varName -> "${varName}.value()" } ?: p.value!!.text
            })
            append(')')
        }

        val modifier = buildString {
            append("Modifier")
            webModifiersToExtract
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

        val allStyleVariables = mutableMapOf<BoundModifierParameter, String>().apply {
            modifierChainInfo.entries.forEach { entry ->
                this.putAll(entry.associatedStyleVariableNames())
            }
        }

        val imports = run {
            val importsBuilder = mutableSetOf<String>()
            importsBuilder.add("com.varabyte.kobweb.silk.style.CssStyle")
            if (allStyleVariables.isNotEmpty()) {
                importsBuilder.add("com.varabyte.kobweb.compose.css.StyleVariable")
                allStyleVariables.forEach { (parameter, _) ->
                    parameter.type.imports.forEach { fqn ->
                        importsBuilder.add(fqn)
                    }
                }
            }
            importsBuilder.sorted().map { FqName(it) }
        }

        val cssStyleText = buildString {
            allStyleVariables.forEach { (parameter, varName) ->
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

        val inlineModifierReplacementText = buildString {
            append("$styleName.toModifier()")
            attrModifiersToLeaveInPlace.forEach { modifierEntry ->
                append('.')
                append(modifierEntry.toText())
            }
            allStyleVariables.forEach { (parameter, varName) ->
                append(".setVariable(")
                append(varName)
                append(", ")
                append(parameter.value!!.text)
                append(")")
            }
        }

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
                    val existingImports = ktFile.importDirectives.mapNotNull { it.importPath?.pathStr }.toSet()

                    val importStrs = buildString {
                        imports
                            .filter { it.asString() !in existingImports }
                            .forEach { import -> appendLine("import ${import.asString()}") }
                    }
                    val dummyImports = psiFactory.createFile(importStrs).importList!!
                    ktFile.importList?.apply {
                        dummyImports.imports.forEach { add(it) }
                    } ?: run {
                        ktFile.add(dummyImports)
                    }

                    // Next, handle the Modifier -> CssStyle extraction
                    val dummyFile = psiFactory.createFile(cssStyleText)
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

                    val replacementExpr = psiFactory.createExpression(inlineModifierReplacementText)
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
            ACTION_TITLE,
            null,
            editor.document
        )
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

        val entries = chainedCalls.map { (funcDefn, callExpr, ) ->
            val funcCall = callExpr.resolveToCall()?.singleFunctionCallOrNull()
            val argMapping = funcCall?.argumentMapping ?: emptyMap()

            val parameters = mutableListOf<BoundModifierParameter>()
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

                            BoundModifierParameter.Value(valueArgument.text, isGlobal)
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

                        BoundModifierParameter.Type(
                            qualifiedRender.split(Regex("[<>, ]")).filter { it.isNotBlank() && it.trim() !in setOf("*", "in", "out") }.toSet(),
                            paramSymbol.returnType.render(
                                KaTypeRendererForSource.WITH_SHORT_NAMES,
                                position = Variance.INVARIANT
                            )
                        )
                    } else null

                    BoundModifierParameter(
                        paramSymbol.name.asString(),
                        paramType ?: BoundModifierParameter.Type(
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
}