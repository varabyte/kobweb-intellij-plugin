package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.route.DeletePackageMappingFileQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.RemovePackageMappingQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.RemoveRouteOverrideQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.ReplaceRouteValueQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.PAGE_FQN
import com.varabyte.kobweb.intellij.util.kobweb.route.PACKAGE_MAPPING_FQN
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteValueProblem
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteValueSimplification
import com.varabyte.kobweb.intellij.util.kobweb.route.findPageRouteResolver
import com.varabyte.kobweb.intellij.util.kobweb.route.pageFileName
import com.varabyte.kobweb.intellij.util.kobweb.route.resolve
import com.varabyte.kobweb.intellij.util.kobweb.route.routeValueExpression
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.psi.unescapedText
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports `@Page` route overrides and `@file:PackageMapping` values that don't change anything (e.g. `@Page("post")`
 * in `Post.kt`), or that could be written more simply (e.g. `@Page("{post}")` in `Post.kt` can be `@Page("{}")`).
 *
 * Kobweb recommends leaving routes to be generated from file and package names where possible, so that it's easy to
 * tell where a URL comes from.
 */
class RedundantRouteInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
                super.visitAnnotationEntry(annotationEntry)
                when {
                    annotationEntry.isAnnotationOfType(PAGE_FQN) -> checkPage(annotationEntry)
                    annotationEntry.isAnnotationOfType(PACKAGE_MAPPING_FQN)
                        && annotationEntry.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.FILE
                        -> checkPackageMapping(annotationEntry)
                }
            }

            private fun checkPage(pageAnnotation: KtAnnotationEntry) {
                val page = (pageAnnotation.parent as? KtModifierList)?.parent as? KtNamedFunction ?: return
                if (!page.isTopLevel) return
                val routeOverrideExpr = pageAnnotation.routeValueExpression ?: return
                val routeOverride = routeOverrideExpr.unescapedText
                // Invalid overrides are reported separately, so don't pile on
                if (RouteValueProblem.findInRouteOverride(routeOverride).isNotEmpty()) return

                val resolver = page.findPageRouteResolver() ?: return
                val pagePackage = page.containingKtFile.packageFqName
                when (
                    val simplification =
                        RouteValueSimplification.forRouteOverride(resolver, pagePackage, page.pageFileName, routeOverride)
                ) {
                    RouteValueSimplification.Redundant -> holder.registerProblem(
                        routeOverrideExpr,
                        UxGlobals.textBundle.message(
                            "inspection.redundant.route.override.display.text",
                            resolver.resolve(page, null)?.value.orEmpty(),
                        ),
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        RemoveRouteOverrideQuickFix(),
                    )

                    is RouteValueSimplification.Shorten -> holder.registerProblem(
                        routeOverrideExpr,
                        UxGlobals.textBundle.message(
                            "inspection.redundant.route.shorten.display.text",
                            simplification.simplerValue,
                        ),
                        ReplaceRouteValueQuickFix(simplification.simplerValue),
                    )

                    null -> {}
                }
            }

            private fun checkPackageMapping(annotationEntry: KtAnnotationEntry) {
                val packageMappingExpr = annotationEntry.routeValueExpression ?: return
                val packageMapping = packageMappingExpr.unescapedText
                // Invalid package mappings are reported separately, so don't pile on
                if (RouteValueProblem.findInPackageMapping(packageMapping).isNotEmpty()) return

                val file = annotationEntry.containingKtFile
                val pkg = file.packageFqName
                // Kobweb ignores package mappings outside the pages package, which is reported separately
                val resolver = annotationEntry.findPageRouteResolver() ?: return
                if (!pkg.isSubpackageOf(resolver.pagesPackage)) return

                when (val simplification = RouteValueSimplification.forPackageMapping(pkg, packageMapping)) {
                    RouteValueSimplification.Redundant -> {
                        // Package mappings usually get a file to themselves, which there's no point keeping around
                        val isOnlyContent = file.declarations.isEmpty()
                            && file.fileAnnotationList?.annotationEntries == listOf(annotationEntry)
                        holder.registerProblem(
                            annotationEntry,
                            UxGlobals.textBundle.message("inspection.redundant.route.package.mapping.display.text"),
                            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                            *listOfNotNull<LocalQuickFix>(
                                DeletePackageMappingFileQuickFix().takeIf { isOnlyContent },
                                RemovePackageMappingQuickFix(),
                            ).toTypedArray(),
                        )
                    }

                    is RouteValueSimplification.Shorten -> holder.registerProblem(
                        packageMappingExpr,
                        UxGlobals.textBundle.message(
                            "inspection.redundant.route.shorten.display.text",
                            simplification.simplerValue,
                        ),
                        ReplaceRouteValueQuickFix(simplification.simplerValue),
                    )

                    null -> {}
                }
            }
        }
    }
}
