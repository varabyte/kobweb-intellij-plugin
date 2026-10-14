package com.varabyte.kobweb.intellij.util.kobweb.style

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.NonEmptyInputValidator
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.refactoring.KotlinNamesValidator
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPackageDirective

private fun KtFile.getTopLevelDeclarationNames(): Set<String> {
    return declarations.asSequence()
        .mapNotNull { it as? KtNamedDeclaration }
        .mapNotNull { it.name }
        .toSet()
}

private fun KtPackageDirective?.getTopLevelDeclarationNames(
    project: Project,
): Set<String> {
    val packageFqName = this?.fqName?.asString() ?: ""
    val psiPackage = JavaPsiFacade.getInstance(project).findPackage(packageFqName) ?: return emptySet()

    val scope = GlobalSearchScope.projectScope(project)
    return psiPackage.getFiles(scope)
        .asSequence()
        .filterIsInstance<KtFile>()
        .flatMap { file -> file.getTopLevelDeclarationNames() }
        .toSet()
}

class StyleNameErrorValidator(private val project: Project, private val existingDeclarations: Set<String>) : InputValidatorEx {
    private val nonEmptyValidator = NonEmptyInputValidator()
    private val namesValidator = KotlinNamesValidator()
    override fun checkInput(inputString: String): Boolean {
        // For the "non-empty" case, we don't return an error string, but we still want to prevent the user from
        // pressing OK. It should be obvious why from context.
        if (!nonEmptyValidator.checkInput(inputString)) return false
        return super.checkInput(inputString)
    }

    override fun getErrorText(inputString: String): String? {
        return when {
            !nonEmptyValidator.checkInput(inputString) -> null // Empty text doesn't need an annoying error message
            inputString in existingDeclarations -> "There is already a conflicting declaration with the same name"
            namesValidator.isKeyword(inputString, project) -> "This name is a reserved Kotlin keyword"
            !namesValidator.isIdentifier(inputString, project) -> "This name contains illegal characters for a Kotlin property"

            else -> null
        }
    }
}

const val CSS_STYLE_SUFFIX = "Style"

class StyleNameWarningValidator : InputValidatorEx {
    override fun getErrorText(inputString: String): String? {
        return when {
            !inputString.endsWith(CSS_STYLE_SUFFIX) -> "CssStyle property names should end with the \"$CSS_STYLE_SUFFIX\" suffix."
            else -> null
        }
    }
}

fun KtFile.createStyleNameErrorValidator(): StyleNameErrorValidator {
    return StyleNameErrorValidator(project, this.packageDirective.getTopLevelDeclarationNames(project))
}
