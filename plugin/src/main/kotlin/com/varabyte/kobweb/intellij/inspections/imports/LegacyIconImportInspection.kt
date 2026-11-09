package com.varabyte.kobweb.intellij.inspections.imports

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.quickfixes.imports.MigrateImportQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.qualifiedIconPackages
import com.varabyte.kobweb.intellij.util.kotlin.isDeclared
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/** The package that each prebuilt `silk-icons-<id>` artifact puts its icons under, in a subpackage named `<id>`. */
private val SILK_ICONS_PACKAGE = FqName("com.varabyte.kobweb.silk.components.icons")

/**
 * Reports imports from a prebuilt `silk-icons-*` artifact (e.g. `silk-icons-fa`) for an icon set that the module
 * also generates itself (e.g. with `kobweb { app { icons { fa() } } }`), and offers to import the generated icons
 * instead.
 *
 * Both come from the same code generator, so icons have the same names in each. However, the module may generate a
 * different version of the icon set than the artifact's, which can be missing some of them. So a problem is only
 * reported if everything the import brings in that the file uses also exists in the generated bindings (which also
 * means they have to be generated first).
 */
class LegacyIconImportInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file as? KtFile ?: return KtVisitorVoid.EMPTY_VISITOR
        if (!file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR
        val iconPackages = file.findKobwebProject()?.qualifiedIconPackages.orEmpty()
        if (iconPackages.isEmpty()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitImportDirective(importDirective: KtImportDirective) {
                super.visitImportDirective(importDirective)
                val importedFqName = importDirective.importedFqName ?: return
                // For a star import, the imported name is the package itself
                val oldPackage = if (importDirective.isAllUnder) importedFqName else importedFqName.parent()
                if (oldPackage.isRoot || oldPackage.parent() != SILK_ICONS_PACKAGE) return
                val newPackage = iconPackages[oldPackage.shortName().asString()] ?: return

                val newFqName = if (importDirective.isAllUnder) newPackage else newPackage.child(importedFqName.shortName())
                val isMigratable = analyze(importDirective) {
                    if (importDirective.isAllUnder) {
                        file.usedNamesFrom(oldPackage).all { name -> newPackage.child(name).isDeclared() }
                    } else {
                        newFqName.isDeclared()
                    }
                }
                if (!isMigratable) return

                holder.registerProblem(
                    importDirective.importedReference ?: importDirective,
                    UxGlobals.textBundle.message("inspection.legacy.icon.import.display.text", oldPackage, newPackage),
                    MigrateImportQuickFix(newFqName.asString()),
                )
            }
        }
    }
}

/**
 * The names of the top-level declarations in [packageName] that this file's code refers to, e.g. `FaHouse` for a call to
 * `FaHouse()` that resolves to `com.varabyte.kobweb.silk.components.icons.fa.FaHouse`.
 */
context(kaSession: KaSession)
private fun KtFile.usedNamesFrom(packageName: FqName): Set<Name> = with(kaSession) {
    PsiTreeUtil.findChildrenOfType(this@usedNamesFrom, KtNameReferenceExpression::class.java)
        .filter { it.getStrictParentOfType<KtImportList>() == null && it.getStrictParentOfType<KtPackageDirective>() == null }
        .mapNotNullTo(mutableSetOf()) { nameExpr ->
            val fqName = when (val symbol = nameExpr.mainReference.resolveToSymbol()) {
                is KaCallableSymbol -> symbol.callableId?.asSingleFqName()
                is KaClassLikeSymbol -> symbol.classId?.asSingleFqName()
                else -> null
            }
            fqName?.takeIf { !it.isRoot && it.parent() == packageName }?.shortName()
        }
}
