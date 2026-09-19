package com.varabyte.kobweb.intellij.util.kobweb.style

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.NonEmptyInputValidator
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidator
import org.jetbrains.kotlin.idea.refactoring.KotlinNamesValidator
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration

/**
 * Get all top level declaration names, given a [KtFile], returning them as dot-separated paths.
 *
 * This also includes declarations nested in top-level objects.
 *
 * For example:
 * ```
 * class WidgetStyle = CssStyle { ... }
 * object Variables {
 *     val WidgetBackgroundColorVar by StyleVariable()
 * }
 * @Composable fun Widget() { /* ... */ }
 * ```
 * will return { "WidgetStyle", "Variables.WidgetBackgroundColorVar", "Widget" }.
 */
private fun KtFile.getTopLevelDeclarationNames(): Set<String> {
    return declarations.asSequence()
        .mapNotNull { it as? KtNamedDeclaration }
        .mapNotNull { it.name }
        .toSet()
}

private fun collectTopLevelNames(
    declaration: KtDeclaration,
    results: MutableSet<String>
) {
    if (declaration !is KtNamedDeclaration) return

    val name = declaration.name ?: return
    results.add(name)
}

class StyleNameValidator(private val project: Project, private val existingDeclarations: Set<String>) : InputValidatorEx {
    private val nonEmptyValidator = NonEmptyInputValidator()
    private val namesValidator = KotlinNamesValidator()
    override fun checkInput(inputString: String): Boolean {
        // For the "non-empty" case, we don't return an error string, but we still want to prevent the user from
        // pressing OK. It should be obvious why from context.
        if (!nonEmptyValidator.checkInput(inputString)) return false
        return super.checkInput(inputString)
    }

    override fun getErrorText(inputString: String): String? {
        if (!nonEmptyValidator.checkInput(inputString)) return null // Empty text doesn't need an annoying error message
        if (inputString in existingDeclarations) return "There is already a conflicting declaration with the same name"
        if (namesValidator.isKeyword(inputString, project)) return "This name is a reserved Kotlin keyword"
        if (!namesValidator.isIdentifier(inputString, project)) return "This name contains illegal characters for a Kotlin property"
        return null
    }
}

fun KtFile.createStyleNameValidator(): StyleNameValidator {
    return StyleNameValidator(project, this.getTopLevelDeclarationNames())
}
