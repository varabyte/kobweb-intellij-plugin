package com.varabyte.kobweb.intellij.inspections.imports

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.varabyte.kobweb.intellij.quickfixes.imports.MigrateImportQuickFix
import com.varabyte.kobweb.intellij.services.imports.ImportMigrationService
import com.varabyte.kobweb.intellij.util.kobweb.imports.ImportMigrations
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.psi.replaceImportedFqName
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.resolution.KaSuccessCallInfo
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Reports imports of declarations that have moved (see [ImportMigrationService]), and offers to import them from their
 * new location instead.
 *
 * See [ImportMigrations] for why we update imports instead of relying on `ReplaceWith`.
 *
 * A problem is only reported if the new location exists in the user's version of the library, and if every use of the
 * imported name in the file would still resolve after the change. The latter protects calls to overloads that didn't
 * move (e.g. ones deprecated in favor of a different signature), which should be fixed first.
 */
class OutdatedImportInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file as? KtFile ?: return KtVisitorVoid.EMPTY_VISITOR
        if (!file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR
        val migrations = ImportMigrationService.instance.migrations
        if (migrations.isEmpty()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitImportDirective(importDirective: KtImportDirective) {
                super.visitImportDirective(importDirective)
                if (importDirective.isAllUnder) return
                val oldFqName = importDirective.importedFqName ?: return
                val newFqName = migrations.findMigration(importDirective) ?: return
                // Checked last, as it's the most expensive check
                if (!importDirective.keepsUsagesResolvedWhenReplacedWith(newFqName)) return

                holder.registerProblem(
                    importDirective.importedReference ?: importDirective,
                    UxGlobals.textBundle.message("inspection.outdated.import.display.text", oldFqName, newFqName),
                    MigrateImportQuickFix(newFqName.asString()),
                )
            }
        }
    }
}

/**
 * Returns the newest place that [importDirective]'s declaration moved to which exists in the user's version of the
 * library, or null if there isn't one.
 *
 * Analysis only runs if the declaration has moved, so this is cheap for most imports.
 */
private fun ImportMigrations.findMigration(importDirective: KtImportDirective): FqName? {
    val fqName = importDirective.importedFqName ?: return null
    val targets = findMigrationTargets(fqName).ifEmpty { return null }
    return analyze(importDirective) { targets.firstOrNull { it.isDeclared() } }
}

/**
 * Returns true if a top-level class, type alias, function, or property with this fully qualified name is visible from
 * the module being analyzed.
 */
context(kaSession: KaSession)
private fun FqName.isDeclared(): Boolean = with(kaSession) {
    if (isRoot) return@with false
    val classId = ClassId.topLevel(this@isDeclared)
    findClass(classId) != null
        || findTypeAlias(classId) != null
        || findTopLevelCallables(parent(), shortName()).any()
}

/**
 * Returns true if every use of the name this directive imports would still resolve if it imported [newFqName] instead.
 *
 * This checks a copy of the file with the import already replaced, which is expensive, but leaves it to the compiler
 * to work out which overloads exist in the new location.
 *
 * Note: Only uses that mention the name are checked, so implicit uses of imported operators (e.g. `getValue` for `by`
 * delegation) are not.
 */
private fun KtImportDirective.keepsUsagesResolvedWhenReplacedWith(newFqName: FqName): Boolean {
    val importedName = aliasName ?: importedFqName?.shortName()?.asString() ?: return false
    val fileCopy = containingKtFile.copy() as KtFile
    PsiTreeUtil.findSameElementInCopy(this, fileCopy).replaceImportedFqName(newFqName)

    val usages = PsiTreeUtil.findChildrenOfType(fileCopy, KtSimpleNameExpression::class.java).filter { nameExpr ->
        nameExpr.getReferencedName() == importedName
            && nameExpr.getStrictParentOfType<KtImportList>() == null
            && nameExpr.getStrictParentOfType<KtPackageDirective>() == null
    }
    if (usages.isEmpty()) return true

    // `analyzeCopy` is the Analysis API's entry point for in-memory copies of real files. It analyzes the copy in the
    // context of the original file's module, and `PREFER_SELF` makes the copy's contents (including the replaced
    // import) win over the original file's, which declares all the same things.
    return analyzeCopy(fileCopy, KaDanglingFileResolutionMode.PREFER_SELF) {
        usages.all { nameExpr ->
            // Resolve calls as a whole, as a call to an overload that no longer exists still resolves its name to the
            // candidates it was checked against
            val call = (nameExpr.parent as? KtCallExpression)?.takeIf { it.calleeExpression == nameExpr }
            if (call != null) {
                call.resolveToCall() is KaSuccessCallInfo
            } else {
                nameExpr.mainReference.resolveToSymbol() != null
            }
        }
    }
}
