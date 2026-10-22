package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.route.RemovePackageMappingQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.ReplaceRouteValueQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.PAGE_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.kobwebDisplayPath
import com.varabyte.kobweb.intellij.util.kobweb.route.PACKAGE_MAPPING_FQN
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteValueProblem
import com.varabyte.kobweb.intellij.util.kobweb.route.findDuplicatePackageMappings
import com.varabyte.kobweb.intellij.util.kobweb.route.findPageRouteResolver
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
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports `@Page` route overrides and `@file:PackageMapping` values that Kobweb rejects (by skipping the page, or
 * failing the build), or that it accepts but are almost certainly mistakes (e.g. "a//b" or "{post").
 */
class InvalidRouteInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
                super.visitAnnotationEntry(annotationEntry)
                when {
                    annotationEntry.isAnnotationOfType(PAGE_FQN) -> checkPage(annotationEntry)
                    annotationEntry.isAnnotationOfType(PACKAGE_MAPPING_FQN)
                        && annotationEntry.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.FILE
                        -> reportAnyPackageMappingProblems(annotationEntry)
                }
            }

            private fun checkPage(pageAnnotation: KtAnnotationEntry) {
                val page = (pageAnnotation.parent as? KtModifierList)?.parent as? KtNamedFunction ?: return
                if (!page.isTopLevel) return

                // If the route override isn't a string literal (e.g. it's a constant), we can't check it
                val routeOverrideExpr = pageAnnotation.routeValueExpression
                if (pageAnnotation.valueArguments.isNotEmpty() && routeOverrideExpr == null) return
                // Kobweb treats blank overrides the same as no override
                val routeOverride = routeOverrideExpr?.unescapedText?.takeIf { it.isNotBlank() }

                if (routeOverrideExpr != null && routeOverride != null) {
                    RouteValueProblem.findInRouteOverride(routeOverride).reportAll(routeOverrideExpr, routeOverride)
                }

                val resolver = page.findPageRouteResolver() ?: return
                val isAbsolute = routeOverride?.startsWith('/') == true
                if (!isAbsolute && !page.containingKtFile.packageFqName.isSubpackageOf(resolver.pagesPackage)) {
                    holder.registerProblem(
                        routeOverrideExpr ?: pageAnnotation,
                        UxGlobals.textBundle.message(
                            "inspection.invalid.route.outside.pages.package.display.text",
                            resolver.pagesPackage.asString(),
                        ),
                        *listOfNotNull(routeOverride?.let { ReplaceRouteValueQuickFix("/$it") }).toTypedArray(),
                    )
                    return
                }

                val route = resolver.resolve(page, routeOverride) ?: return
                if (route.removePrefix("/").substringBefore('/') == "api") {
                    holder.registerProblem(
                        routeOverrideExpr ?: pageAnnotation,
                        UxGlobals.textBundle.message("inspection.invalid.route.reserved.api.display.text", route),
                        // Kobweb fails the build over this, unlike the other problems here
                        ProblemHighlightType.GENERIC_ERROR,
                    )
                }
            }

            private fun reportAnyPackageMappingProblems(annotationEntry: KtAnnotationEntry) {
                val packageMappingExpr = annotationEntry.routeValueExpression ?: return
                val packageMapping = packageMappingExpr.unescapedText
                // A blank package mapping, if allowed, would be destructive to the final route (unlike a blank route
                // override, which just means no override)
                if (packageMapping.isBlank()) {
                    holder.registerProblem(
                        packageMappingExpr,
                        UxGlobals.textBundle.message("inspection.invalid.route.package.mapping.blank.display.text"),
                        ProblemHighlightType.GENERIC_ERROR,
                        RemovePackageMappingQuickFix(),
                    )
                    return
                }

                val duplicates = annotationEntry.findDuplicatePackageMappings()
                if (duplicates.isNotEmpty()) {
                    holder.registerProblem(
                        annotationEntry,
                        UxGlobals.textBundle.message(
                            "inspection.invalid.route.package.mapping.duplicate.display.text",
                            annotationEntry.containingKtFile.packageFqName.asString(),
                            duplicates.joinToString { "\"${it.containingKtFile.kobwebDisplayPath}\"" },
                        ),
                        ProblemHighlightType.GENERIC_ERROR,
                        RemovePackageMappingQuickFix(),
                    )
                }

                RouteValueProblem.findInPackageMapping(packageMapping).reportAll(
                    packageMappingExpr,
                    packageMapping,
                    isPackageMapping = true,
                )

                val resolver = annotationEntry.findPageRouteResolver() ?: return
                if (!annotationEntry.containingKtFile.packageFqName.isSubpackageOf(resolver.pagesPackage)) {
                    holder.registerProblem(
                        annotationEntry,
                        UxGlobals.textBundle.message(
                            "inspection.invalid.route.package.mapping.outside.pages.package.display.text",
                            resolver.pagesPackage.asString(),
                        ),
                    )
                }
            }

            private fun List<RouteValueProblem>.reportAll(
                valueExpr: KtStringTemplateExpression,
                value: String,
                isPackageMapping: Boolean = false,
            ) {
                val problems = this
                val valueRange = ElementManipulators.getValueTextRange(valueExpr)
                // Problem ranges are within the (decoded) value, so they only line up with the string's text if it
                // doesn't contain escape sequences, e.g. "\u003F" for "?". Otherwise, just highlight the whole value.
                // (Escape sequences are always longer than the characters they stand for, so if they're present, the
                // lengths differ.)
                val hasEscapes = valueRange.length != value.length

                for (problem in problems) {
                    val segment = problem.range.substring(value)
                    val (message, fix) = when (problem.kind) {
                        RouteValueProblem.Kind.MISPLACED_DYNAMIC_SEGMENT -> {
                            if (isPackageMapping) {
                                UxGlobals.textBundle.message("inspection.invalid.route.package.mapping.misplaced.display.text")
                            } else {
                                UxGlobals.textBundle.message("inspection.invalid.route.misplaced.display.text")
                            } to null
                        }

                        RouteValueProblem.Kind.MALFORMED_DYNAMIC_SEGMENT ->
                            UxGlobals.textBundle.message("inspection.invalid.route.malformed.display.text", segment) to null

                        RouteValueProblem.Kind.EMPTY_SEGMENT ->
                            UxGlobals.textBundle.message("inspection.invalid.route.empty.segment.display.text") to
                                ReplaceRouteValueQuickFix(value.replace(Regex("/{2,}"), "/"))

                        RouteValueProblem.Kind.INVALID_CHARACTER ->
                            UxGlobals.textBundle.message("inspection.invalid.route.invalid.character.display.text") to null
                    }

                    holder.registerProblem(
                        valueExpr,
                        if (hasEscapes) valueRange else problem.range.shiftRight(valueRange.startOffset),
                        message,
                        *listOfNotNull<LocalQuickFix>(fix).toTypedArray(),
                    )
                }
            }
        }
    }
}
